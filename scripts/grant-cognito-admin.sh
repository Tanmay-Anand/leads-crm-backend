#!/usr/bin/env bash
#
# Lets the API's IAM role call the Cognito admin APIs on this deployment's user pool.
#
# provision-infra.sh gives the instance role SSM and ECR-pull only, which is everything the host
# needs to *run* the container but nothing it needs to provision users. Every admin call behind
# POST /users, the enable/disable toggle and the password reset therefore fails with
# AccessDeniedException until this policy exists - and it fails at the point a real person clicks
# the button, not at boot, which is why the gap survives a clean deploy unnoticed.
#
# Scoped to one pool ARN rather than "*": a compromised host can manage the users of this CRM and
# nothing else in the account.
#
#   AWS_PROFILE=personal ./scripts/grant-cognito-admin.sh
#
# provision-infra.sh runs this itself, so a fresh provision needs nothing extra. Run it directly
# to fix an already-provisioned host without re-provisioning. Re-running is safe: put-role-policy
# replaces the policy document wholesale.

set -euo pipefail

# Git Bash on Windows rewrites any argument that looks like a Unix path into a Windows one, which
# corrupts IAM ARNs before the CLI ever sees them. Harmless elsewhere.
export MSYS_NO_PATHCONV=1

REGION="${AWS_REGION:-ap-south-1}"
NAME="${NAME:-leads-crm}"
POOL_NAME="${POOL_NAME:-leads-crm}"
ROLE_NAME="${ROLE_NAME:-${NAME}-ec2-role}"
POLICY_NAME="${POLICY_NAME:-${NAME}-cognito-admin}"

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"

# ── Which pool ────────────────────────────────────────────────────────────────
# AWS_COGNITO_USER_POOL_ID wins when set, so this can be pointed at a pool that was created by
# hand or lives under a different name. Otherwise it is discovered the same way configure-host.sh
# discovers it, which keeps the two from ever disagreeing about which pool is "the" pool.

POOL_ID="${AWS_COGNITO_USER_POOL_ID:-}"

if [[ -z "$POOL_ID" ]]; then
  POOL_ID="$(aws cognito-idp list-user-pools --max-results 60 --region "$REGION" \
    --query "UserPools[?Name=='${POOL_NAME}'].Id | [0]" --output text)"
fi

# Not fatal. provision-infra.sh calls this before the pool necessarily exists, and a half-failed
# infra provision is a worse outcome than a missing policy you can add in one command later.
if [[ "$POOL_ID" == "None" || -z "$POOL_ID" ]]; then
  echo "No Cognito user pool named ${POOL_NAME} in ${REGION}." >&2
  echo "Run scripts/provision-cognito.sh first, then re-run this script." >&2
  exit 0
fi

if ! aws iam get-role --role-name "$ROLE_NAME" >/dev/null 2>&1; then
  echo "IAM role ${ROLE_NAME} does not exist. Run scripts/provision-infra.sh first." >&2
  exit 1
fi

POOL_ARN="arn:aws:cognito-idp:${REGION}:${ACCOUNT_ID}:userpool/${POOL_ID}"

# ── The policy ────────────────────────────────────────────────────────────────
# Exactly the calls DefaultCognitoService makes, and no others. AdminDeleteUser is on the list
# because createUser's compensating delete runs whenever the local transaction after a successful
# Cognito create rolls back - without it that path leaves an orphaned identity behind.

aws iam put-role-policy --role-name "$ROLE_NAME" --policy-name "$POLICY_NAME" \
  --policy-document "{
    \"Version\": \"2012-10-17\",
    \"Statement\": [{
      \"Effect\": \"Allow\",
      \"Action\": [
        \"cognito-idp:AdminCreateUser\",
        \"cognito-idp:AdminGetUser\",
        \"cognito-idp:AdminSetUserPassword\",
        \"cognito-idp:AdminAddUserToGroup\",
        \"cognito-idp:AdminEnableUser\",
        \"cognito-idp:AdminDisableUser\",
        \"cognito-idp:AdminDeleteUser\"
      ],
      \"Resource\": \"${POOL_ARN}\"
    }]
  }"

echo "Role ${ROLE_NAME} may now administer users in ${POOL_ID}"
echo "  ${POOL_ARN}"
