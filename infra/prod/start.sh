#!/usr/bin/env bash
#
# Brings the stack up, fetching the database password from Secrets Manager first.
#
# The password is passed to compose through the environment and never written to disk. That is the
# whole point of RDS managed master passwords: the only durable copy lives in Secrets Manager, and
# the host proves its identity with its instance role rather than holding a credential.
#
#   ./start.sh          bring up with whatever API_IMAGE .env currently names
#
# The deploy workflow rewrites API_IMAGE in .env and then calls this.

set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo ".env not found. Copy .env.example and fill it in." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

if [[ -z "${APP_DB_SECRET_ARN:-}" ]]; then
  echo "APP_DB_SECRET_ARN is not set in .env. It is printed by scripts/provision-rds.sh." >&2
  exit 1
fi

echo "Fetching the database password from Secrets Manager..."
# RDS stores it as a JSON document, {"username": "...", "password": "..."}.
SECRET_JSON="$(aws secretsmanager get-secret-value \
  --region "${AWS_REGION}" --secret-id "${APP_DB_SECRET_ARN}" \
  --query SecretString --output text)"

APP_DB_PASSWORD="$(printf '%s' "$SECRET_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin)["password"])')"
export APP_DB_PASSWORD

if [[ -z "$APP_DB_PASSWORD" ]]; then
  echo "Could not read the password from ${APP_DB_SECRET_ARN}." >&2
  exit 1
fi

echo "Starting..."
docker compose --env-file .env up -d

echo "Waiting for the API to report healthy..."
for i in $(seq 1 40); do
  STATUS="$(docker inspect --format '{{.State.Health.Status}}' leads-crm-api 2>/dev/null || echo starting)"
  echo "  attempt ${i}: ${STATUS}"
  if [[ "$STATUS" == "healthy" ]]; then
    echo "Up."
    exit 0
  fi
  sleep 5
done

echo "Did not become healthy. Recent logs:" >&2
docker compose logs --tail 50 api >&2
exit 1
