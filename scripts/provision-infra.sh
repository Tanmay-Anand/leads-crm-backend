#!/usr/bin/env bash
#
# Provisions the AWS footprint the API deploys onto.
#
# READ THIS BEFORE RUNNING. It creates billable resources in whichever account your CLI is
# authenticated to. At the defaults below, expect roughly:
#
#   EC2 t3.small        ~$15/mo   (t3.micro is free-tier eligible for 12 months on a new account,
#                                  but 1GB RAM is tight for Spring Boot plus Postgres)
#   EBS 20GB gp3        ~$1.80/mo
#   Elastic IP          free while attached to a running instance, ~$3.60/mo if left unattached
#   ECR                 ~$0.10/GB-month, so pennies
#   CloudFront          ~$1/mo at demo traffic
#   SSM, IAM            free
#
# Roughly $18/mo. Tear it all down with scripts/destroy-infra.sh.
#
#   AWS_PROFILE=personal ./scripts/provision-infra.sh
#
# Re-running is safe: every step checks for an existing resource first.

set -euo pipefail

# Git Bash on Windows rewrites any argument that looks like a Unix path into a Windows one, which
# corrupts SSM parameter names and IAM ARNs before the CLI ever sees them. Harmless elsewhere.
export MSYS_NO_PATHCONV=1

REGION="${AWS_REGION:-ap-south-1}"
NAME="${NAME:-leads-crm}"
INSTANCE_TYPE="${INSTANCE_TYPE:-t3.small}"
GITHUB_REPO="${GITHUB_REPO:-Tanmay-Anand/leads-crm-backend}"

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"

echo "Account:  ${ACCOUNT_ID}"
echo "Region:   ${REGION}"
echo "Instance: ${INSTANCE_TYPE}"
echo

# ── ECR ───────────────────────────────────────────────────────────────────────

if aws ecr describe-repositories --region "$REGION" --repository-names "$NAME-backend" >/dev/null 2>&1; then
  echo "ECR repository ${NAME}-backend already exists"
else
  aws ecr create-repository --region "$REGION" --repository-name "${NAME}-backend" \
    --image-scanning-configuration scanOnPush=true >/dev/null
  echo "Created ECR repository ${NAME}-backend"
fi

# Untagged layers accumulate on every deploy and are pure cost otherwise.
aws ecr put-lifecycle-policy --region "$REGION" --repository-name "${NAME}-backend" \
  --lifecycle-policy-text '{
    "rules": [{
      "rulePriority": 1,
      "description": "Expire untagged images after 7 days",
      "selection": {"tagStatus": "untagged", "countType": "sinceImagePushed", "countUnit": "days", "countNumber": 7},
      "action": {"type": "expire"}
    }]
  }' >/dev/null
echo "ECR lifecycle policy applied"

# ── Instance role ─────────────────────────────────────────────────────────────
# SSM replaces SSH entirely: no key pair, no port 22, no private key in GitHub secrets.

INSTANCE_ROLE="${NAME}-ec2-role"

if aws iam get-role --role-name "$INSTANCE_ROLE" >/dev/null 2>&1; then
  echo "Instance role ${INSTANCE_ROLE} already exists"
else
  aws iam create-role --role-name "$INSTANCE_ROLE" \
    --assume-role-policy-document '{
      "Version": "2012-10-17",
      "Statement": [{
        "Effect": "Allow",
        "Principal": {"Service": "ec2.amazonaws.com"},
        "Action": "sts:AssumeRole"
      }]
    }' >/dev/null
  echo "Created instance role ${INSTANCE_ROLE}"
fi

aws iam attach-role-policy --role-name "$INSTANCE_ROLE" \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
# Pull only. The host has no business pushing images.
aws iam attach-role-policy --role-name "$INSTANCE_ROLE" \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly

if ! aws iam get-instance-profile --instance-profile-name "$INSTANCE_ROLE" >/dev/null 2>&1; then
  aws iam create-instance-profile --instance-profile-name "$INSTANCE_ROLE" >/dev/null
  aws iam add-role-to-instance-profile --instance-profile-name "$INSTANCE_ROLE" --role-name "$INSTANCE_ROLE"
  echo "Created instance profile ${INSTANCE_ROLE}"
  # IAM is eventually consistent; RunInstances fails if the profile is not visible yet.
  sleep 12
fi

# ── GitHub OIDC ───────────────────────────────────────────────────────────────
# Lets the deploy workflow assume a role using a short-lived GitHub-signed token, so there are no
# AWS access keys stored in the repository at all.

OIDC_ARN="arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"

if aws iam get-open-id-connect-provider --open-id-connect-provider-arn "$OIDC_ARN" >/dev/null 2>&1; then
  echo "GitHub OIDC provider already exists"
else
  aws iam create-open-id-connect-provider \
    --url https://token.actions.githubusercontent.com \
    --client-id-list sts.amazonaws.com \
    --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1 >/dev/null
  echo "Created GitHub OIDC provider"
fi

DEPLOY_ROLE="${NAME}-github-deploy"

# The trust policy is the security boundary: only this repository, only on the main branch.
#
# Two patterns, because GitHub changed the shape of the sub claim. It used to be
#   repo:OWNER/REPO:ref:refs/heads/main
# and is now
#   repo:OWNER@<ownerId>/REPO@<repoId>:ref:refs/heads/main
# where the numeric ids are immutable, so a repository that is renamed or deleted and recreated
# cannot inherit the trust of the old one. Matching only the old form is why the first deploy
# failed with "Not authorized to perform sts:AssumeRoleWithWebIdentity".
#
# The wildcards sit only where the numeric ids go, between @ and the next literal, so this cannot
# be satisfied by a different owner or repository name.
GITHUB_OWNER="${GITHUB_REPO%%/*}"
GITHUB_NAME="${GITHUB_REPO##*/}"

TRUST_POLICY=$(cat <<TRUST
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Federated": "${OIDC_ARN}"},
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {"token.actions.githubusercontent.com:aud": "sts.amazonaws.com"},
      "StringLike": {
        "token.actions.githubusercontent.com:sub": [
          "repo:${GITHUB_REPO}:ref:refs/heads/main",
          "repo:${GITHUB_OWNER}@*/${GITHUB_NAME}@*:ref:refs/heads/main"
        ]
      }
    }
  }]
}
TRUST
)

if aws iam get-role --role-name "$DEPLOY_ROLE" >/dev/null 2>&1; then
  aws iam update-assume-role-policy --role-name "$DEPLOY_ROLE" --policy-document "$TRUST_POLICY"
  echo "Deploy role ${DEPLOY_ROLE} already exists, trust policy refreshed"
else
  aws iam create-role --role-name "$DEPLOY_ROLE" --assume-role-policy-document "$TRUST_POLICY" >/dev/null
  echo "Created deploy role ${DEPLOY_ROLE}"
fi

# Scoped to exactly what the workflow does: push an image, and run a deploy command on one host.
aws iam put-role-policy --role-name "$DEPLOY_ROLE" --policy-name "${NAME}-deploy" \
  --policy-document "{
    \"Version\": \"2012-10-17\",
    \"Statement\": [
      {
        \"Effect\": \"Allow\",
        \"Action\": [\"ecr:GetAuthorizationToken\"],
        \"Resource\": \"*\"
      },
      {
        \"Effect\": \"Allow\",
        \"Action\": [
          \"ecr:BatchCheckLayerAvailability\", \"ecr:CompleteLayerUpload\", \"ecr:InitiateLayerUpload\",
          \"ecr:PutImage\", \"ecr:UploadLayerPart\", \"ecr:BatchGetImage\", \"ecr:GetDownloadUrlForLayer\"
        ],
        \"Resource\": \"arn:aws:ecr:${REGION}:${ACCOUNT_ID}:repository/${NAME}-backend\"
      },
      {
        \"Effect\": \"Allow\",
        \"Action\": [\"ssm:SendCommand\"],
        \"Resource\": [
          \"arn:aws:ssm:${REGION}::document/AWS-RunShellScript\",
          \"arn:aws:ec2:${REGION}:${ACCOUNT_ID}:instance/*\"
        ]
      },
      {
        \"Effect\": \"Allow\",
        \"Action\": [\"ssm:GetCommandInvocation\", \"ssm:ListCommandInvocations\"],
        \"Resource\": \"*\"
      }
    ]
  }"
echo "Deploy role policy applied"

# ── Security group ────────────────────────────────────────────────────────────

VPC_ID="$(aws ec2 describe-vpcs --region "$REGION" --filters Name=isDefault,Values=true \
  --query 'Vpcs[0].VpcId' --output text)"

SG_ID="$(aws ec2 describe-security-groups --region "$REGION" \
  --filters Name=group-name,Values="${NAME}-sg" Name=vpc-id,Values="$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || echo "None")"

if [[ "$SG_ID" == "None" || -z "$SG_ID" ]]; then
  SG_ID="$(aws ec2 create-security-group --region "$REGION" \
    --group-name "${NAME}-sg" --vpc-id "$VPC_ID" \
    --description "Leads CRM API host" --query GroupId --output text)"
  echo "Created security group ${SG_ID}"

  # Port 80 only, and deliberately no port 22: shell access is via SSM Session Manager, which
  # needs no inbound rule at all. Postgres is not exposed; it is reachable only inside compose.
  aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
    --protocol tcp --port 80 --cidr 0.0.0.0/0 >/dev/null
  echo "Opened port 80"
else
  echo "Security group ${SG_ID} already exists"
fi

# ── Instance ──────────────────────────────────────────────────────────────────

INSTANCE_ID="$(aws ec2 describe-instances --region "$REGION" \
  --filters Name=tag:Name,Values="${NAME}-api" Name=instance-state-name,Values=running,pending \
  --query 'Reservations[0].Instances[0].InstanceId' --output text 2>/dev/null || echo "None")"

if [[ "$INSTANCE_ID" == "None" || -z "$INSTANCE_ID" ]]; then
  AMI_ID="$(aws ssm get-parameter --region "$REGION" \
    --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-6.1-x86_64 \
    --query 'Parameter.Value' --output text)"

  # Installs Docker and the compose plugin, and lays down /opt/leads-crm so the first deploy has
  # somewhere to land. The .env is written by the operator afterwards, not baked into the image.
  USER_DATA="$(base64 -w0 <<'CLOUDINIT'
#!/bin/bash
set -eux
dnf update -y
dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user

mkdir -p /usr/local/lib/docker/cli-plugins
curl -SL https://github.com/docker/compose/releases/download/v2.32.4/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 2GB of swap. The box has 2GB of RAM and roughly 1.5GB of it is the JVM, Postgres and Docker,
# so a spike would otherwise hit the OOM killer, which picks the largest process: the API.
# Swap turns that failure into slowness, which is recoverable.
dd if=/dev/zero of=/swapfile bs=1M count=2048
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
# The JVM and Postgres should stay resident; swap is the emergency margin, not a working tier.
sysctl -w vm.swappiness=10
echo 'vm.swappiness=10' >> /etc/sysctl.conf

mkdir -p /opt/leads-crm
CLOUDINIT
)"

  INSTANCE_ID="$(aws ec2 run-instances --region "$REGION" \
    --image-id "$AMI_ID" \
    --instance-type "$INSTANCE_TYPE" \
    --security-group-ids "$SG_ID" \
    --iam-instance-profile "Name=${INSTANCE_ROLE}" \
    --user-data "$USER_DATA" \
    --metadata-options "HttpTokens=required"     --credit-specification "CpuCredits=standard" \
    --block-device-mappings '[{"DeviceName":"/dev/xvda","Ebs":{"VolumeSize":20,"VolumeType":"gp3","DeleteOnTermination":true}}]' \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${NAME}-api}]" \
    --query 'Instances[0].InstanceId' --output text)"

  echo "Launched instance ${INSTANCE_ID}, waiting for it to run..."
  aws ec2 wait instance-running --region "$REGION" --instance-ids "$INSTANCE_ID"
else
  echo "Instance ${INSTANCE_ID} already exists"
fi

# ── Elastic IP ────────────────────────────────────────────────────────────────
# Without this the public IP changes on every stop/start, breaking CloudFront's origin.

EIP_ALLOC="$(aws ec2 describe-addresses --region "$REGION" \
  --filters Name=tag:Name,Values="${NAME}-eip" \
  --query 'Addresses[0].AllocationId' --output text 2>/dev/null || echo "None")"

if [[ "$EIP_ALLOC" == "None" || -z "$EIP_ALLOC" ]]; then
  EIP_ALLOC="$(aws ec2 allocate-address --region "$REGION" --domain vpc \
    --tag-specifications "ResourceType=elastic-ip,Tags=[{Key=Name,Value=${NAME}-eip}]" \
    --query AllocationId --output text)"
  echo "Allocated Elastic IP ${EIP_ALLOC}"
fi

aws ec2 associate-address --region "$REGION" \
  --instance-id "$INSTANCE_ID" --allocation-id "$EIP_ALLOC" >/dev/null
PUBLIC_IP="$(aws ec2 describe-addresses --region "$REGION" --allocation-ids "$EIP_ALLOC" \
  --query 'Addresses[0].PublicIp' --output text)"
echo "Public IP: ${PUBLIC_IP}"

# ── Summary ───────────────────────────────────────────────────────────────────

DEPLOY_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${DEPLOY_ROLE}"

cat <<SUMMARY

Done.

  Instance      ${INSTANCE_ID}
  Public IP     ${PUBLIC_IP}
  Security grp  ${SG_ID}
  ECR           ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${NAME}-backend
  Deploy role   ${DEPLOY_ROLE_ARN}

GitHub repository secrets to add (Settings > Secrets and variables > Actions):

  AWS_DEPLOY_ROLE_ARN   ${DEPLOY_ROLE_ARN}
  EC2_INSTANCE_ID       ${INSTANCE_ID}
  SERVICE_URL           (the CloudFront URL, once scripts/provision-cloudfront.sh has run)

Next:
  1. scripts/provision-cloudfront.sh      - HTTPS in front of the instance
  2. Write /opt/leads-crm/.env on the host - see infra/prod/.env.example
  3. Push to main                          - the workflow does the rest
SUMMARY
