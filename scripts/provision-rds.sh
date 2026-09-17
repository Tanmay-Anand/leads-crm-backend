#!/usr/bin/env bash
#
# Provisions the managed Postgres the API uses, replacing the container that previously ran
# alongside it on the EC2 host.
#
# Three things this buys over a container on the app host:
#   - the data survives the instance being terminated, which destroy-infra.sh does routinely
#   - automated backups and point-in-time recovery
#   - roughly 200MB of RAM back on a 2GB box, where the JVM was competing with Postgres
#
# ── On the password ──────────────────────────────────────────────────────────
#
# Created with --manage-master-user-password, so RDS generates the master password itself and
# stores it in Secrets Manager. Nobody types it, nobody sees it, and it is never written to a
# file. The host reads it at deploy time using its instance role. This is the reason the compose
# file no longer carries APP_DB_PASSWORD.
#
#   AWS_PROFILE=personal ./scripts/provision-rds.sh
#
# Takes 5-10 minutes. Re-running is safe.

set -euo pipefail

# Git Bash on Windows rewrites any argument that looks like a Unix path into a Windows one, which
# corrupts SSM parameter names and IAM ARNs before the CLI ever sees them. Harmless elsewhere.
export MSYS_NO_PATHCONV=1

REGION="${AWS_REGION:-ap-south-1}"
NAME="${NAME:-leads-crm}"
DB_INSTANCE="${DB_INSTANCE:-${NAME}-db}"
# Free-tier eligible. ARM (t4g) is cheaper than t3 for identical specs.
DB_CLASS="${DB_CLASS:-db.t4g.micro}"
# Postgres identifiers cannot contain hyphens, so this is not "leads-crm".
DB_NAME="${DB_NAME:-leadscrm}"
DB_USER="${DB_USER:-leadscrm}"

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"

echo "Account:  ${ACCOUNT_ID}"
echo "Region:   ${REGION}"
echo "Class:    ${DB_CLASS}"
echo

VPC_ID="$(aws ec2 describe-vpcs --region "$REGION" --filters Name=isDefault,Values=true \
  --query 'Vpcs[0].VpcId' --output text)"

APP_SG_ID="$(aws ec2 describe-security-groups --region "$REGION" \
  --filters Name=group-name,Values="${NAME}-sg" Name=vpc-id,Values="$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text)"

if [[ "$APP_SG_ID" == "None" || -z "$APP_SG_ID" ]]; then
  echo "Application security group ${NAME}-sg not found. Run provision-infra.sh first." >&2
  exit 1
fi

# ── Database security group ───────────────────────────────────────────────────
# Ingress is granted to the application security group rather than to a CIDR, so the rule stays
# correct if the instance is replaced and gets a different address.

DB_SG_ID="$(aws ec2 describe-security-groups --region "$REGION" \
  --filters Name=group-name,Values="${NAME}-db-sg" Name=vpc-id,Values="$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || echo "None")"

if [[ "$DB_SG_ID" == "None" || -z "$DB_SG_ID" ]]; then
  DB_SG_ID="$(aws ec2 create-security-group --region "$REGION" \
    --group-name "${NAME}-db-sg" --vpc-id "$VPC_ID" \
    --description "Leads CRM database" --query GroupId --output text)"
  echo "Created database security group ${DB_SG_ID}"
fi

if aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$DB_SG_ID" \
    --protocol tcp --port 5432 --source-group "$APP_SG_ID" >/dev/null 2>&1; then
  echo "Allowed 5432 from ${APP_SG_ID}"
else
  echo "Ingress rule already present"
fi

# ── Subnet group ──────────────────────────────────────────────────────────────

if aws rds describe-db-subnet-groups --region "$REGION" --db-subnet-group-name "${NAME}-subnets" \
    >/dev/null 2>&1; then
  echo "Subnet group already exists"
else
  SUBNET_IDS="$(aws ec2 describe-subnets --region "$REGION" \
    --filters Name=vpc-id,Values="$VPC_ID" --query 'Subnets[].SubnetId' --output text)"
  aws rds create-db-subnet-group --region "$REGION" \
    --db-subnet-group-name "${NAME}-subnets" \
    --db-subnet-group-description "Leads CRM database subnets" \
    --subnet-ids $SUBNET_IDS >/dev/null
  echo "Created subnet group"
fi

# ── Instance ──────────────────────────────────────────────────────────────────

if aws rds describe-db-instances --region "$REGION" --db-instance-identifier "$DB_INSTANCE" \
    >/dev/null 2>&1; then
  echo "Database ${DB_INSTANCE} already exists"
else
  echo "Creating ${DB_INSTANCE}..."
  aws rds create-db-instance --region "$REGION" \
    --db-instance-identifier "$DB_INSTANCE" \
    --db-instance-class "$DB_CLASS" \
    --engine postgres \
    --engine-version 17.11 \
    --allocated-storage 20 \
    --storage-type gp3 \
    --db-name "$DB_NAME" \
    --master-username "$DB_USER" \
    --manage-master-user-password \
    --vpc-security-group-ids "$DB_SG_ID" \
    --db-subnet-group-name "${NAME}-subnets" \
    --no-publicly-accessible \
    --backup-retention-period 1 \
    --no-multi-az \
    --storage-encrypted \
    --no-auto-minor-version-upgrade \
    --copy-tags-to-snapshot \
    --tags "Key=Name,Value=${DB_INSTANCE}" >/dev/null
  echo "Waiting for it to become available, this takes 5-10 minutes..."
  aws rds wait db-instance-available --region "$REGION" --db-instance-identifier "$DB_INSTANCE"
fi

ENDPOINT="$(aws rds describe-db-instances --region "$REGION" --db-instance-identifier "$DB_INSTANCE" \
  --query 'DBInstances[0].Endpoint.Address' --output text)"
SECRET_ARN="$(aws rds describe-db-instances --region "$REGION" --db-instance-identifier "$DB_INSTANCE" \
  --query 'DBInstances[0].MasterUserSecret.SecretArn' --output text)"

echo "Endpoint: ${ENDPOINT}"

# ── Let the host read the password ────────────────────────────────────────────
# Scoped to this one secret, so a compromised host cannot read anything else in Secrets Manager.

aws iam put-role-policy --role-name "${NAME}-ec2-role" --policy-name "${NAME}-db-secret" \
  --policy-document "{
    \"Version\": \"2012-10-17\",
    \"Statement\": [{
      \"Effect\": \"Allow\",
      \"Action\": [\"secretsmanager:GetSecretValue\"],
      \"Resource\": \"${SECRET_ARN}\"
    }]
  }"
echo "Instance role may now read the database secret"

cat <<SUMMARY

Done.

  Endpoint    ${ENDPOINT}
  Database    ${DB_NAME}
  User        ${DB_USER}
  Secret      ${SECRET_ARN}
  Security    ${DB_SG_ID}, reachable only from ${APP_SG_ID}

In /opt/leads-crm/.env on the host:

  APP_DB_HOST=${ENDPOINT}
  APP_DB_NAME=${DB_NAME}
  APP_DB_USERNAME=${DB_USER}
  APP_DB_SECRET_ARN=${SECRET_ARN}

APP_DB_PASSWORD is deliberately absent: infra/prod/start.sh fetches it from Secrets Manager at
container start, so the password never lands in a file on disk.

The crm schema still has to exist. scripts/init-rds-schema.sh creates it.
SUMMARY
