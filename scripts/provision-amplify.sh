#!/usr/bin/env bash
#
# Configures an existing Amplify Hosting app: environment variables and rewrite rules.
#
# It does NOT create the app or connect the repository. Amplify's GitHub connection is a browser
# OAuth flow that installs the AWS Amplify GitHub App on your account; doing it from the CLI would
# mean handing a GitHub personal access token to a script, which is both worse security and more
# work than clicking through it once. Create and connect the app in the console first:
#
#   https://console.aws.amazon.com/amplify  ->  Create new app  ->  GitHub
#
# Then run this to set everything that is fiddly to get right by hand.
#
# ── Two ways to reach the API ────────────────────────────────────────────────
#
# The browser loads the app over HTTPS, so it cannot call a plain HTTP API: that is blocked as
# mixed content. There are two ways to solve it, and this script supports both.
#
#   API_MODE=proxy (default)
#     Amplify reverse-proxies /leads-crm/* through to the EC2 host. The browser only ever talks to
#     its own origin, so there is no mixed content AND no CORS at all. Costs nothing and needs no
#     certificate. Set ORIGIN_IP to the instance's Elastic IP.
#
#   API_MODE=direct
#     The browser calls the API directly at API_URL, which must be HTTPS — in practice a
#     CloudFront distribution in front of the instance. Needs scripts/provision-cloudfront.sh to
#     have succeeded, and needs APP_CORS_ORIGINS set on the host.
#
# Proxy mode exists because a new AWS account cannot create CloudFront distributions until AWS
# verifies it, which blocks direct mode on day one. It is not a lesser option: one less service,
# one less bill, and CORS stops being something that can be misconfigured.
#
#   ORIGIN_IP=15.252.85.128 AWS_PROFILE=personal ./scripts/provision-amplify.sh
#   API_URL=https://xxx.cloudfront.net API_MODE=direct AWS_PROFILE=personal ./scripts/provision-amplify.sh
#
# Re-running is safe: it overwrites the same settings with the same values.

set -euo pipefail

# Git Bash on Windows rewrites any argument that looks like a Unix path into a Windows one, which
# corrupts SSM parameter names and IAM ARNs before the CLI ever sees them. Harmless elsewhere.
export MSYS_NO_PATHCONV=1

REGION="${AWS_REGION:-ap-south-1}"
APP_NAME="${APP_NAME:-leads-crm-frontend}"
BRANCH="${BRANCH:-main}"
API_MODE="${API_MODE:-proxy}"

COGNITO_POOL_ID="${AWS_COGNITO_USER_POOL_ID:-}"
COGNITO_CLIENT_ID="${AWS_COGNITO_CLIENT_ID:-}"
API_URL="${API_URL:-}"
ORIGIN_IP="${ORIGIN_IP:-}"

# Falls back to reading these off the pool, so they do not have to be passed by hand.
if [[ -z "$COGNITO_POOL_ID" ]]; then
  COGNITO_POOL_ID="$(aws cognito-idp list-user-pools --max-results 60 --region "$REGION" \
    --query "UserPools[?Name=='leads-crm'].Id | [0]" --output text)"
fi
if [[ -z "$COGNITO_CLIENT_ID" && "$COGNITO_POOL_ID" != "None" ]]; then
  COGNITO_CLIENT_ID="$(aws cognito-idp list-user-pool-clients --region "$REGION" \
    --user-pool-id "$COGNITO_POOL_ID" --max-results 60 \
    --query "UserPoolClients[?ClientName=='leads-crm-web'].ClientId | [0]" --output text)"
fi

APP_ID="$(aws amplify list-apps --region "$REGION" \
  --query "apps[?name=='${APP_NAME}'].appId | [0]" --output text 2>/dev/null || echo "None")"

if [[ "$APP_ID" == "None" || -z "$APP_ID" ]]; then
  echo "No Amplify app named ${APP_NAME} in ${REGION}." >&2
  echo "Create it in the console and connect the GitHub repository first, then re-run." >&2
  exit 1
fi

APP_URL="https://${BRANCH}.${APP_ID}.amplifyapp.com"

# ── Rewrite rules ─────────────────────────────────────────────────────────────
# The asset rule has to come first. Without it the SPA catch-all would swallow requests for the
# JS and CSS bundles and hand back index.html, and nothing would load.

ASSET_RULE='{
  "source": "/<*>.<css|gif|ico|jpg|jpeg|js|map|png|svg|txt|webp|woff|woff2>",
  "target": "/<*>.<css|gif|ico|jpg|jpeg|js|map|png|svg|txt|webp|woff|woff2>",
  "status": "200"
}'

# Status 200 rather than a redirect: the URL the user sees must not change, or the router loses it.
SPA_RULE='{
  "source": "/<*>",
  "target": "/index.html",
  "status": "200"
}'

if [[ "$API_MODE" == "proxy" ]]; then
  if [[ -z "$ORIGIN_IP" ]]; then
    ORIGIN_IP="$(aws ec2 describe-addresses --region "$REGION" \
      --filters Name=tag:Name,Values=leads-crm-eip --query 'Addresses[0].PublicIp' --output text 2>/dev/null || echo "None")"
  fi
  if [[ "$ORIGIN_IP" == "None" || -z "$ORIGIN_IP" ]]; then
    echo "Proxy mode needs ORIGIN_IP (the instance Elastic IP)." >&2
    exit 1
  fi

  # Amplify rewrites need a hostname, not a bare IP. nip.io resolves <ip>.nip.io to <ip> with no
  # setup — a stopgap so this works before you own a domain. Swap it for a real one when you do:
  # it is a third-party DNS service in the request path.
  API_HOST="${ORIGIN_IP}.nip.io"

  # The proxy rule must precede the SPA catch-all, or /leads-crm/* would render index.html.
  API_RULE="{
    \"source\": \"/leads-crm/<*>\",
    \"target\": \"http://${API_HOST}/leads-crm/<*>\",
    \"status\": \"200\"
  }"

  CUSTOM_RULES="[${API_RULE},${ASSET_RULE},${SPA_RULE}]"
  # Same origin as the app itself, so the API client's fetches are same-origin.
  SERVER_URL="${APP_URL}"

  echo "Mode:   proxy (Amplify -> http://${API_HOST})"
else
  if [[ -z "$API_URL" ]]; then
    echo "Direct mode needs API_URL, the HTTPS API endpoint." >&2
    exit 1
  fi
  CUSTOM_RULES="[${ASSET_RULE},${SPA_RULE}]"
  SERVER_URL="${API_URL}"

  echo "Mode:   direct (browser -> ${API_URL})"
fi

echo "App:    ${APP_ID} (${APP_NAME})"
echo "Branch: ${BRANCH}"
echo

aws amplify update-app --region "$REGION" --app-id "$APP_ID" \
  --environment-variables \
    "VITE_SERVER_URL=${SERVER_URL},VITE_DOMAIN=${APP_URL},VITE_AWS_REGION=${REGION},VITE_AWS_COGNITO_USER_POOL_ID=${COGNITO_POOL_ID},VITE_AWS_COGNITO_USER_POOL_CLIENT_ID=${COGNITO_CLIENT_ID}" \
  >/dev/null
echo "Environment variables set"

aws amplify update-app --region "$REGION" --app-id "$APP_ID" \
  --custom-rules "$CUSTOM_RULES" >/dev/null
echo "Rewrite rules configured"

cat <<SUMMARY

Done.

  App URL   ${APP_URL}
  API via   ${SERVER_URL}

SUMMARY

if [[ "$API_MODE" == "proxy" ]]; then
  cat <<PROXY
Requests are same-origin, so no CORS configuration is needed on the host. APP_CORS_ORIGINS can be
left as-is; it is simply never consulted.

Note that port 80 on the instance is still open to the internet, because Amplify's edge has no
fixed IP range to narrow it to. Every endpoint requires a Cognito token, but the API is reachable
over plain HTTP by IP. Move to CloudFront once AWS verifies the account, then re-run with
API_MODE=direct and narrow the security group.
PROXY
else
  cat <<DIRECT
Remaining, and easy to forget — the API has to be told to accept this origin:

  On the EC2 host, in /opt/leads-crm/.env:
    APP_CORS_ORIGINS=${APP_URL}

  Then:
    docker compose --env-file .env up -d

Until that is done the site loads and every API call is blocked by CORS.
DIRECT
fi

cat <<TRIGGER

Trigger a build (or just push to ${BRANCH}):
  aws amplify start-job --region ${REGION} --app-id ${APP_ID} --branch-name ${BRANCH} --job-type RELEASE
TRIGGER
