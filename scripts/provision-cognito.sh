#!/usr/bin/env bash
#
# Provisions the Cognito user pool this CRM authenticates against.
#
# Review before running. It creates billable-but-free-tier resources in whichever AWS account
# your CLI is currently authenticated to. Nothing here reads or writes credentials: authenticate
# yourself first, with `aws configure --profile <name>` or `aws sso login`.
#
#   AWS_PROFILE=tanmay ./scripts/provision-cognito.sh
#
# Re-running is safe: every step checks for an existing resource first.

set -euo pipefail

REGION="${AWS_REGION:-ap-south-1}"
POOL_NAME="${POOL_NAME:-leads-crm}"
CLIENT_NAME="${CLIENT_NAME:-leads-crm-web}"

# The tenant every seeded record belongs to. Any UUID works; it just has to match between the
# Cognito user and the x-tenant-id the frontend sends.
TENANT_ID="${TENANT_ID:-11111111-1111-1111-1111-111111111111}"

ADMIN_EMAIL="${ADMIN_EMAIL:-}"

if [[ -z "$ADMIN_EMAIL" ]]; then
  echo "Set ADMIN_EMAIL to the address of the first user, e.g. ADMIN_EMAIL=you@company.com $0" >&2
  exit 1
fi

echo "Account: $(aws sts get-caller-identity --query Account --output text)"
echo "Region:  $REGION"
echo

# ── User pool ─────────────────────────────────────────────────────────────────
# custom:tenantId is what TenantAware reads to scope every query, so the attribute has to exist
# on the pool before any user can be given one.

POOL_ID="$(aws cognito-idp list-user-pools --max-results 60 --region "$REGION" \
  --query "UserPools[?Name=='${POOL_NAME}'].Id | [0]" --output text)"

if [[ "$POOL_ID" == "None" || -z "$POOL_ID" ]]; then
  echo "Creating user pool ${POOL_NAME}..."
  POOL_ID="$(aws cognito-idp create-user-pool \
    --region "$REGION" \
    --pool-name "$POOL_NAME" \
    --username-attributes email \
    --auto-verified-attributes email \
    --schema \
      'Name=tenantId,AttributeDataType=String,Mutable=true,Required=false,StringAttributeConstraints={MinLength=1,MaxLength=64}' \
    --policies 'PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true,RequireSymbols=false}' \
    --query 'UserPool.Id' --output text)"
  echo "  created ${POOL_ID}"
else
  echo "User pool ${POOL_NAME} already exists: ${POOL_ID}"
fi

# ── Groups ────────────────────────────────────────────────────────────────────
# WebSecurityConfig maps cognito:groups straight onto Spring authorities, so these names are the
# role vocabulary the API understands.

for GROUP in TENANT_ADMIN TENANT_USER PLATFORM_ADMIN PLATFORM_USER; do
  if aws cognito-idp get-group --region "$REGION" --user-pool-id "$POOL_ID" --group-name "$GROUP" \
      >/dev/null 2>&1; then
    echo "Group ${GROUP} already exists"
  else
    aws cognito-idp create-group --region "$REGION" --user-pool-id "$POOL_ID" \
      --group-name "$GROUP" >/dev/null
    echo "Created group ${GROUP}"
  fi
done

# ── App client ────────────────────────────────────────────────────────────────
# A public client with no secret: the browser cannot keep one. USER_SRP_AUTH is what Amplify uses.

CLIENT_ID="$(aws cognito-idp list-user-pool-clients --region "$REGION" --user-pool-id "$POOL_ID" \
  --max-results 60 --query "UserPoolClients[?ClientName=='${CLIENT_NAME}'].ClientId | [0]" --output text)"

if [[ "$CLIENT_ID" == "None" || -z "$CLIENT_ID" ]]; then
  echo "Creating app client ${CLIENT_NAME}..."
  CLIENT_ID="$(aws cognito-idp create-user-pool-client \
    --region "$REGION" \
    --user-pool-id "$POOL_ID" \
    --client-name "$CLIENT_NAME" \
    --no-generate-secret \
    --explicit-auth-flows ALLOW_USER_SRP_AUTH ALLOW_REFRESH_TOKEN_AUTH \
    --read-attributes email custom:tenantId \
    --write-attributes email custom:tenantId \
    --query 'UserPoolClient.ClientId' --output text)"
  echo "  created ${CLIENT_ID}"
else
  echo "App client ${CLIENT_NAME} already exists: ${CLIENT_ID}"
fi

# ── First user ────────────────────────────────────────────────────────────────
# Created with a temporary password, so the first sign-in goes through the new-password challenge
# the sign-in screen already handles. No password is set or printed by this script.

if aws cognito-idp admin-get-user --region "$REGION" --user-pool-id "$POOL_ID" \
    --username "$ADMIN_EMAIL" >/dev/null 2>&1; then
  echo "User ${ADMIN_EMAIL} already exists"
else
  echo "Creating user ${ADMIN_EMAIL}..."
  aws cognito-idp admin-create-user \
    --region "$REGION" \
    --user-pool-id "$POOL_ID" \
    --username "$ADMIN_EMAIL" \
    --user-attributes \
      Name=email,Value="$ADMIN_EMAIL" \
      Name=email_verified,Value=true \
      Name=custom:tenantId,Value="$TENANT_ID" \
    --desired-delivery-mediums EMAIL >/dev/null
  echo "  created. A temporary password has been emailed to ${ADMIN_EMAIL}."
fi

aws cognito-idp admin-add-user-to-group \
  --region "$REGION" --user-pool-id "$POOL_ID" \
  --username "$ADMIN_EMAIL" --group-name TENANT_ADMIN

echo
echo "Done. Put these in your .env files:"
echo
echo "  leads-crm-backend/.env"
echo "    AWS_REGION=${REGION}"
echo "    AWS_COGNITO_USER_POOL_ID=${POOL_ID}"
echo "    AWS_COGNITO_CLIENT_ID=${CLIENT_ID}"
echo
echo "  leads-crm-frontend/.env"
echo "    VITE_AWS_REGION=${REGION}"
echo "    VITE_AWS_COGNITO_USER_POOL_ID=${POOL_ID}"
echo "    VITE_AWS_COGNITO_USER_POOL_CLIENT_ID=${CLIENT_ID}"
echo
echo "Tenant for the first user: ${TENANT_ID}"
