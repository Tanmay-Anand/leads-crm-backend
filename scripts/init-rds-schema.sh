#!/usr/bin/env bash
#
# Creates the `crm` schema in RDS.
#
# Hibernate creates tables but never the schema that contains them, and the container Postgres
# used to get this from an init-schema.sql that RDS has no equivalent of. So it runs once, here.
#
# The database is in a private subnet, so this executes on the EC2 host over SSM rather than
# connecting from a laptop. The password is read from Secrets Manager by the host itself, using
# its instance role — it is never fetched to, or printed on, the machine running this script.
#
# The remote script is base64-encoded before being handed to SSM. Embedding shell directly in the
# --parameters JSON means escaping quotes through two layers, which is how the first version of
# this broke; encoding it removes the problem entirely.
#
#   AWS_PROFILE=personal ./scripts/init-rds-schema.sh
#
# Idempotent: CREATE SCHEMA IF NOT EXISTS.

set -euo pipefail

# Git Bash on Windows rewrites any argument that looks like a Unix path into a Windows one.
export MSYS_NO_PATHCONV=1

REGION="${AWS_REGION:-ap-south-1}"
NAME="${NAME:-leads-crm}"
DB_NAME="${DB_NAME:-leadscrm}"
DB_USER="${DB_USER:-leadscrm}"

INSTANCE_ID="$(aws ec2 describe-instances --region "$REGION" \
  --filters Name=tag:Name,Values="${NAME}-api" Name=instance-state-name,Values=running \
  --query 'Reservations[0].Instances[0].InstanceId' --output text)"

ENDPOINT="$(aws rds describe-db-instances --region "$REGION" --db-instance-identifier "${NAME}-db" \
  --query 'DBInstances[0].Endpoint.Address' --output text)"
SECRET_ARN="$(aws rds describe-db-instances --region "$REGION" --db-instance-identifier "${NAME}-db" \
  --query 'DBInstances[0].MasterUserSecret.SecretArn' --output text)"

echo "Host:     ${INSTANCE_ID}"
echo "Database: ${ENDPOINT}"
echo

# The remote body is a QUOTED heredoc, so nothing expands locally: the values it needs are
# injected ahead of it as plain assignments. An unquoted heredoc is how the first version mangled
# the single quotes in the SQL.
REMOTE_SCRIPT="$(printf 'REGION=%s
SECRET_ARN=%s
ENDPOINT=%s
DB_USER=%s
DB_NAME=%s
'   "$REGION" "$SECRET_ARN" "$ENDPOINT" "$DB_USER" "$DB_NAME")
$(cat <<'REMOTE'
set -euo pipefail

SECRET_JSON=$(aws secretsmanager get-secret-value --region "$REGION"   --secret-id "$SECRET_ARN" --query SecretString --output text)
PGPASSWORD=$(printf '%s' "$SECRET_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin)["password"])')
export PGPASSWORD

# psql runs in a throwaway container so nothing has to be installed on the host. The password is
# passed through the environment, never on the command line where it would show in the process list.
docker run --rm -e PGPASSWORD postgres:17-alpine   psql -h "$ENDPOINT" -U "$DB_USER" -d "$DB_NAME"   -c 'CREATE SCHEMA IF NOT EXISTS crm;'

echo "--- schemas now present ---"
docker run --rm -e PGPASSWORD postgres:17-alpine   psql -h "$ENDPOINT" -U "$DB_USER" -d "$DB_NAME" -Atc '\dn'
REMOTE
)"

ENCODED="$(printf '%s' "$REMOTE_SCRIPT" | base64 -w0)"

COMMAND_ID="$(aws ssm send-command --region "$REGION" \
  --instance-ids "$INSTANCE_ID" \
  --document-name "AWS-RunShellScript" \
  --comment "Create crm schema" \
  --parameters "commands=[\"echo ${ENCODED} | base64 -d | bash\"]" \
  --query 'Command.CommandId' --output text)"

echo "SSM command ${COMMAND_ID}"
aws ssm wait command-executed --region "$REGION" \
  --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" || true

STATUS="$(aws ssm get-command-invocation --region "$REGION" \
  --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" --query 'Status' --output text)"

echo "--- output ---"
aws ssm get-command-invocation --region "$REGION" \
  --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" \
  --query 'StandardOutputContent' --output text

if [[ "$STATUS" != "Success" ]]; then
  echo "--- errors ---" >&2
  aws ssm get-command-invocation --region "$REGION" \
    --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" \
    --query 'StandardErrorContent' --output text >&2
  exit 1
fi

echo "Schema ready."
