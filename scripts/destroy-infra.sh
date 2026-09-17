#!/usr/bin/env bash
#
# Tears down everything provision-infra.sh and provision-cloudfront.sh created.
#
# This DELETES the EC2 instance and the RDS database. The database is offered a final snapshot
# first, so the data is recoverable unless you decline it.
#
# The Cognito user pool is deliberately NOT deleted: it holds your users, it costs nothing at this
# scale, and rebuilding it means everyone signs up again. Remove it by hand if you really want to.
#
#   AWS_PROFILE=personal ./scripts/destroy-infra.sh
#
# Asks for confirmation before anything irreversible.

set -euo pipefail

# Git Bash on Windows rewrites any argument that looks like a Unix path into a Windows one, which
# corrupts SSM parameter names and IAM ARNs before the CLI ever sees them. Harmless elsewhere.
export MSYS_NO_PATHCONV=1

REGION="${AWS_REGION:-ap-south-1}"
NAME="${NAME:-leads-crm}"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"

echo "This will delete, in account ${ACCOUNT_ID} region ${REGION}:"
echo "  - the ${NAME}-api EC2 instance"
echo "  - the ${NAME}-db RDS database (a final snapshot is offered)"
echo "  - the Elastic IP, security group, ECR repository and images"
echo "  - the CloudFront distribution"
echo "  - the IAM roles and the GitHub OIDC provider"
echo
read -r -p "Type the word destroy to continue: " CONFIRM
if [[ "$CONFIRM" != "destroy" ]]; then
  echo "Aborted."
  exit 1
fi

# ── CloudFront ────────────────────────────────────────────────────────────────
# A distribution has to be disabled and fully propagated before it can be deleted, which takes
# several minutes. Disabling is the part that stops the billing.

DIST_ID="$(aws cloudfront list-distributions \
  --query "DistributionList.Items[?Comment=='${NAME}-api'].Id | [0]" --output text 2>/dev/null || echo "None")"

if [[ "$DIST_ID" != "None" && -n "$DIST_ID" ]]; then
  ETAG="$(aws cloudfront get-distribution-config --id "$DIST_ID" --query ETag --output text)"
  aws cloudfront get-distribution-config --id "$DIST_ID" --query DistributionConfig > /tmp/cf-config.json
  python -c "
import json
c = json.load(open('/tmp/cf-config.json'))
c['Enabled'] = False
json.dump(c, open('/tmp/cf-config.json', 'w'))
"
  aws cloudfront update-distribution --id "$DIST_ID" --distribution-config file:///tmp/cf-config.json \
    --if-match "$ETAG" >/dev/null
  echo "Disabled distribution ${DIST_ID}. Delete it once it finishes propagating:"
  echo "  aws cloudfront delete-distribution --id ${DIST_ID} --if-match \$(aws cloudfront get-distribution-config --id ${DIST_ID} --query ETag --output text)"
fi

# ── RDS ───────────────────────────────────────────────────────────────────────
# Deleted before the instance, because the database security group cannot be removed while the
# database still references it.

if aws rds describe-db-instances --region "$REGION" --db-instance-identifier "${NAME}-db"     >/dev/null 2>&1; then
  read -r -p "Take a final snapshot of ${NAME}-db before deleting it? [Y/n] " SNAP
  if [[ "$SNAP" == "n" ]]; then
    aws rds delete-db-instance --region "$REGION" --db-instance-identifier "${NAME}-db"       --skip-final-snapshot --delete-automated-backups >/dev/null
    echo "Deleting ${NAME}-db with no snapshot..."
  else
    SNAP_ID="${NAME}-db-final-$(date +%Y%m%d%H%M%S)"
    aws rds delete-db-instance --region "$REGION" --db-instance-identifier "${NAME}-db"       --final-db-snapshot-identifier "$SNAP_ID" >/dev/null
    echo "Deleting ${NAME}-db, final snapshot ${SNAP_ID}..."
    echo "  Note: a retained snapshot is billed for its storage."
  fi
  aws rds wait db-instance-deleted --region "$REGION" --db-instance-identifier "${NAME}-db"
  echo "Database deleted"
fi

aws rds delete-db-subnet-group --region "$REGION" --db-subnet-group-name "${NAME}-subnets"   2>/dev/null && echo "Deleted subnet group" || true

# ── EC2 ───────────────────────────────────────────────────────────────────────

INSTANCE_ID="$(aws ec2 describe-instances --region "$REGION" \
  --filters Name=tag:Name,Values="${NAME}-api" Name=instance-state-name,Values=running,pending,stopped \
  --query 'Reservations[0].Instances[0].InstanceId' --output text 2>/dev/null || echo "None")"

if [[ "$INSTANCE_ID" != "None" && -n "$INSTANCE_ID" ]]; then
  aws ec2 terminate-instances --region "$REGION" --instance-ids "$INSTANCE_ID" >/dev/null
  echo "Terminating ${INSTANCE_ID}..."
  aws ec2 wait instance-terminated --region "$REGION" --instance-ids "$INSTANCE_ID"
  echo "Terminated"
fi

# An unattached Elastic IP is charged hourly, so this matters even though it is free while in use.
EIP_ALLOC="$(aws ec2 describe-addresses --region "$REGION" \
  --filters Name=tag:Name,Values="${NAME}-eip" --query 'Addresses[0].AllocationId' --output text 2>/dev/null || echo "None")"
if [[ "$EIP_ALLOC" != "None" && -n "$EIP_ALLOC" ]]; then
  aws ec2 release-address --region "$REGION" --allocation-id "$EIP_ALLOC"
  echo "Released Elastic IP"
fi

SG_ID="$(aws ec2 describe-security-groups --region "$REGION" \
  --filters Name=group-name,Values="${NAME}-sg" --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || echo "None")"
if [[ "$SG_ID" != "None" && -n "$SG_ID" ]]; then
  aws ec2 delete-security-group --region "$REGION" --group-id "$SG_ID" 2>/dev/null \
    && echo "Deleted security group" \
    || echo "Security group still in use; delete it once the instance has fully terminated"
fi

# ── ECR ───────────────────────────────────────────────────────────────────────

aws ecr delete-repository --region "$REGION" --repository-name "${NAME}-backend" --force >/dev/null 2>&1 \
  && echo "Deleted ECR repository" || true

# ── IAM ───────────────────────────────────────────────────────────────────────

INSTANCE_ROLE="${NAME}-ec2-role"
aws iam remove-role-from-instance-profile --instance-profile-name "$INSTANCE_ROLE" --role-name "$INSTANCE_ROLE" 2>/dev/null || true
aws iam delete-instance-profile --instance-profile-name "$INSTANCE_ROLE" 2>/dev/null || true
aws iam detach-role-policy --role-name "$INSTANCE_ROLE" --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore 2>/dev/null || true
aws iam detach-role-policy --role-name "$INSTANCE_ROLE" --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly 2>/dev/null || true
aws iam delete-role --role-name "$INSTANCE_ROLE" 2>/dev/null && echo "Deleted ${INSTANCE_ROLE}" || true

DEPLOY_ROLE="${NAME}-github-deploy"
aws iam delete-role-policy --role-name "$INSTANCE_ROLE" --policy-name "${NAME}-db-secret" 2>/dev/null || true
aws iam delete-role-policy --role-name "$DEPLOY_ROLE" --policy-name "${NAME}-deploy" 2>/dev/null || true
aws iam delete-role --role-name "$DEPLOY_ROLE" 2>/dev/null && echo "Deleted ${DEPLOY_ROLE}" || true

# Shared account-wide: only remove it if nothing else uses GitHub Actions against this account.
read -r -p "Delete the GitHub OIDC provider too? Other repositories may rely on it. [y/N] " RM_OIDC
if [[ "$RM_OIDC" == "y" ]]; then
  aws iam delete-open-id-connect-provider \
    --open-id-connect-provider-arn "arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com" \
    2>/dev/null && echo "Deleted OIDC provider" || true
fi

echo
echo "Done. The Cognito user pool was left in place."
