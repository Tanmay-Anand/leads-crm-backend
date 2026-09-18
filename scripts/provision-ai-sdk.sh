#!/usr/bin/env bash
#
# One-time setup for the ai-query-sdk instance mounted inside this backend, so the pre-meeting
# briefing's AI_NARRATIVE section has something to query. Entity/field/relationship enablement
# and guardrails live in the SDK's own SQLite file (ai-sdk-data/), set at runtime through
# /ai-sdk/configure - not in application.yaml - so a fresh clone answers 403 for every target
# until this has run once.
#
#   AI_SDK_BASE_URL=http://localhost:8090/leads-crm AI_SDK_ADMIN_PASSWORD=... ./scripts/provision-ai-sdk.sh
#
# Idempotent: checks /ai-sdk/status first and skips setup if it is already complete, and
# re-running the entity/guardrail steps only flips flags on the SDK's already-introspected
# schema rather than fabricating one.
#
# Requires: the app already running, curl, jq.

set -euo pipefail

BASE_URL="${AI_SDK_BASE_URL:-http://localhost:8090/leads-crm}"
ADMIN_PASSWORD="${AI_SDK_ADMIN_PASSWORD:-}"

if [[ -z "$ADMIN_PASSWORD" ]]; then
  echo "Set AI_SDK_ADMIN_PASSWORD - the admin password to set (first run) or sign in with" \
       "(later runs), e.g. AI_SDK_ADMIN_PASSWORD=... $0" >&2
  exit 1
fi

for bin in curl jq; do
  command -v "$bin" >/dev/null || { echo "Missing $bin" >&2; exit 1; }
done

# Entities the AI_NARRATIVE section is allowed to see. Lead -> Project is deliberately not a
# relationship to enable: leads-crm-backend's own README ("Deviations §4") says Lead.projectId
# is a plain UUID column with no JPA association, so there is nothing for the SDK's Criteria-API
# traversal to walk there - a project reaches the model as a second query target instead
# (AiSdkQueryClient in pre-meeting-briefing-assistant), never through this relationship list.
TARGET_ENTITIES=("Lead" "Project" "ChannelPartner" "LeadNote" "LeadStatus"
                  "CustomTemperature" "CustomTag" "CustomSourceCategory")

# Never exposed to the LLM, regardless of which entity they sit on - situation, not PII. Masking
# mobile here does not lose WhatsApp-chat grounding: pre-meeting-briefing-assistant's
# LeadsCrmAdapter sends the lead's phone number as an explicit query target field instead of
# leaving the SDK to find it in these (masked) exposed fields.
SENSITIVE_FIELDS=("mobile" "alternateMobile" "email" "mobileNormalized" "tenant"
                   "createdByUserId" "lastModifiedByUserId")

MAX_ROWS=20
MAX_CHILD_DEPTH=1
MAX_PARENT_DEPTH=2

status=$(curl -sf "$BASE_URL/ai-sdk/status")
setup_completed=$(echo "$status" | jq -r '.setupCompleted')

if [[ "$setup_completed" != "true" ]]; then
  otp_generated=$(echo "$status" | jq -r '.otpGenerated')
  if [[ "$otp_generated" != "true" ]]; then
    echo "Setup OTP not generated yet - start the app first, then re-run this script." >&2
    exit 1
  fi

  echo -n "Paste the setup OTP (logged at WARN on the app's first start): "
  read -r OTP

  echo "Completing /ai-sdk/setup..."
  curl -sf -X POST "$BASE_URL/ai-sdk/setup" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg otp "$OTP" --arg password "$ADMIN_PASSWORD" '{otp: $otp, password: $password}')" \
    >/dev/null
else
  echo "Setup already completed - signing in with AI_SDK_ADMIN_PASSWORD."
fi

TOKEN=$(curl -sf -X POST "$BASE_URL/ai-sdk/auth/token" \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg password "$ADMIN_PASSWORD" '{password: $password}')" | jq -r '.token')

if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo "Could not obtain a token - check AI_SDK_ADMIN_PASSWORD." >&2
  exit 1
fi
AUTH=(-H "Authorization: Bearer $TOKEN")

echo "Rescanning the schema..."
curl -sf -X POST "$BASE_URL/ai-sdk/configure/rescan" "${AUTH[@]}" >/dev/null

SCHEMA=$(curl -sf "$BASE_URL/ai-sdk/configure/schema" "${AUTH[@]}")

TARGETS_JSON=$(printf '%s\n' "${TARGET_ENTITIES[@]}" | jq -R . | jq -s .)
SENSITIVE_JSON=$(printf '%s\n' "${SENSITIVE_FIELDS[@]}" | jq -R . | jq -s .)

ENTITIES=$(echo "$SCHEMA" | jq --argjson targets "$TARGETS_JSON" \
  '.entities | map(.enabled = (.entityName as $e | $targets | index($e) != null))')

FIELDS=$(echo "$SCHEMA" | jq --argjson sensitive "$SENSITIVE_JSON" \
  '.fields | map(
     (.fieldName as $f | ($sensitive | index($f) != null)) as $isSensitive
     | .sensitive = $isSensitive
     | .exposedToLlm = (if $isSensitive then false else true end))')

# Only relationships that stay entirely within the enabled-entity set are worth traversing - the
# one that would matter most, Lead -> Project, is never present here because it does not exist
# as a JPA relationship to introspect in the first place (see the comment above TARGET_ENTITIES).
RELATIONSHIPS=$(echo "$SCHEMA" | jq --argjson targets "$TARGETS_JSON" \
  '.relationships | map(
     .traverseEnabled = ((.entityName as $e | $targets | index($e) != null)
                          and (.relatedEntity as $r | $targets | index($r) != null)))')

echo "Saving entity/field/relationship selection..."
curl -sf -X POST "$BASE_URL/ai-sdk/configure/entities" "${AUTH[@]}" \
  -H "Content-Type: application/json" \
  -d "$(jq -n --argjson entities "$ENTITIES" --argjson fields "$FIELDS" --argjson relationships "$RELATIONSHIPS" \
    '{entities: $entities, fields: $fields, relationships: $relationships}')" \
  >/dev/null

GUARDRAILS=$(printf '%s\n' "${TARGET_ENTITIES[@]}" | jq -R \
  --argjson maxRows "$MAX_ROWS" --argjson maxChildDepth "$MAX_CHILD_DEPTH" --argjson maxParentDepth "$MAX_PARENT_DEPTH" \
  '{entityName: ., maxRows: $maxRows, maxChildDepth: $maxChildDepth, maxParentDepth: $maxParentDepth,
    customPromptInstructions: null}' | jq -s .)

echo "Saving guardrails..."
curl -sf -X POST "$BASE_URL/ai-sdk/configure/guardrails" "${AUTH[@]}" \
  -H "Content-Type: application/json" \
  -d "$(jq -n --argjson guardrails "$GUARDRAILS" '{guardrails: $guardrails}')" \
  >/dev/null

echo
echo "Done. Enabled entities: ${TARGET_ENTITIES[*]}"
echo "Verify with a real lead id:"
echo "  curl -X POST $BASE_URL/ai-sdk/query -H \"Authorization: Bearer \$TOKEN\" \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"question\": \"Summarize this lead\", \"targets\": [{\"entity\": \"Lead\", \"id\": \"<uuid>\"}]}'"
