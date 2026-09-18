#!/usr/bin/env bash
#
# Copies the compose stack to /opt/leads-crm on the EC2 host and writes its .env.
#
# This used to be a manual step, because someone had to choose a database password. RDS manages
# that now, so every value is discoverable and the whole thing can be automated. Nothing secret is
# written: .env names the Secrets Manager ARN, and start.sh resolves it at container start.
#
# Files are shipped base64-encoded inside the SSM command, which avoids needing S3 as an
# intermediary for four small files.
#
#   AWS_PROFILE=personal ./scripts/configure-host.sh
#
# Re-running is safe, and is how you roll out a change to docker-compose.yml or nginx.conf.

set -euo pipefail

# Git Bash on Windows rewrites any argument that looks like a Unix path into a Windows one.
export MSYS_NO_PATHCONV=1

REGION="${AWS_REGION:-ap-south-1}"
NAME="${NAME:-leads-crm}"
DB_NAME="${DB_NAME:-leadscrm}"
DB_USER="${DB_USER:-leadscrm}"
POOL_NAME="${POOL_NAME:-leads-crm}"
CLIENT_NAME="${CLIENT_NAME:-leads-crm-web}"

cd "$(dirname "$0")/.."

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"

INSTANCE_ID="$(aws ec2 describe-instances --region "$REGION" \
  --filters Name=tag:Name,Values="${NAME}-api" Name=instance-state-name,Values=running \
  --query 'Reservations[0].Instances[0].InstanceId' --output text)"

ENDPOINT="$(aws rds describe-db-instances --region "$REGION" --db-instance-identifier "${NAME}-db" \
  --query 'DBInstances[0].Endpoint.Address' --output text)"
SECRET_ARN="$(aws rds describe-db-instances --region "$REGION" --db-instance-identifier "${NAME}-db" \
  --query 'DBInstances[0].MasterUserSecret.SecretArn' --output text)"

POOL_ID="$(aws cognito-idp list-user-pools --max-results 60 --region "$REGION" \
  --query "UserPools[?Name=='${POOL_NAME}'].Id | [0]" --output text)"
CLIENT_ID="$(aws cognito-idp list-user-pool-clients --region "$REGION" --user-pool-id "$POOL_ID" \
  --max-results 60 --query "UserPoolClients[?ClientName=='${CLIENT_NAME}'].ClientId | [0]" --output text)"

REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

# ai-sdk needs two values that application.yaml gives no default, so the container exits on boot
# without them. Neither is discoverable from AWS the way everything above is.
#
# The JWT secret only signs the SDK's own 60-minute tokens, so generating a fresh one per run
# costs at most a re-login to the in-CRM AI widget. Pass AI_SDK_JWT_SECRET to keep one stable.
AI_SDK_JWT_SECRET="${AI_SDK_JWT_SECRET:-$(openssl rand -hex 32)}"

# An OpenRouter account credential, so it can only come from the operator. The placeholder is
# deliberately a working default: the API boots and every CRM endpoint behaves, and only the AI
# query widget fails, at call time rather than at startup.
OPENROUTER_API_KEY="${OPENROUTER_API_KEY:-unset-ai-query-disabled}"

# The SDK's own CORS check, which is separate from APP_CORS_ORIGINS and defaults to
# http://localhost:5173. Set it to the Amplify origin to use the widget from the deployed UI.
AI_SDK_ALLOWED_ORIGIN="${AI_SDK_ALLOWED_ORIGIN:-}"

echo "Host:     ${INSTANCE_ID}"
echo "Database: ${ENDPOINT}"
echo "Pool:     ${POOL_ID}"
echo

# API_IMAGE points at :latest for the very first boot. The deploy workflow rewrites it to the
# commit-tagged image on every run, which is what makes rollback a tag change.
ENV_FILE="$(cat <<ENVFILE
API_IMAGE=${REGISTRY}/${NAME}-backend:latest

APP_DB_HOST=${ENDPOINT}
APP_DB_NAME=${DB_NAME}
APP_DB_USERNAME=${DB_USER}
APP_DB_SECRET_ARN=${SECRET_ARN}

AWS_REGION=${REGION}
AWS_COGNITO_USER_POOL_ID=${POOL_ID}
AWS_COGNITO_CLIENT_ID=${CLIENT_ID}

# Empty while the frontend reaches the API through the Amplify proxy: those requests are
# same-origin, so CORS never applies. Set it if you move to a direct CloudFront origin.
APP_CORS_ORIGINS=

# ai-sdk. The first two are mandatory: application.yaml defaults neither, so the container
# exits on boot if either is missing, and nginx then answers 502 with no upstream.
AI_SDK_JWT_SECRET=${AI_SDK_JWT_SECRET}
OPENROUTER_API_KEY=${OPENROUTER_API_KEY}
AI_SDK_ALLOWED_ORIGIN=${AI_SDK_ALLOWED_ORIGIN}
ENVFILE
)"

B64_COMPOSE="$(base64 -w0 < infra/prod/docker-compose.yml)"
B64_NGINX="$(base64 -w0 < infra/prod/nginx.conf)"
B64_START="$(base64 -w0 < infra/prod/start.sh)"
B64_ENV="$(printf '%s' "$ENV_FILE" | base64 -w0)"

REMOTE_SCRIPT="$(cat <<REMOTE
set -euo pipefail
mkdir -p /opt/leads-crm
cd /opt/leads-crm
echo '${B64_COMPOSE}' | base64 -d > docker-compose.yml
echo '${B64_NGINX}'   | base64 -d > nginx.conf
echo '${B64_START}'   | base64 -d > start.sh
echo '${B64_ENV}'     | base64 -d > .env
chmod +x start.sh
chmod 600 .env
ls -la /opt/leads-crm
echo '--- compose config check ---'
docker compose --env-file .env config >/dev/null && echo 'compose file is valid'
REMOTE
)"

ENCODED="$(printf '%s' "$REMOTE_SCRIPT" | base64 -w0)"

COMMAND_ID="$(aws ssm send-command --region "$REGION" \
  --instance-ids "$INSTANCE_ID" \
  --document-name "AWS-RunShellScript" \
  --comment "Configure host" \
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

echo
echo "Host configured. It has no image to run yet; push to main and the workflow will deploy one."
