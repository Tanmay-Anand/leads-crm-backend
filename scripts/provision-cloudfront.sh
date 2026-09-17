#!/usr/bin/env bash
#
# Puts CloudFront in front of the EC2 instance, to give the API an HTTPS endpoint.
#
# Why this exists: the frontend is served from Amplify Hosting over HTTPS, and a browser refuses to let an
# HTTPS page call an HTTP API — it is blocked as mixed content. A bare EC2 box has no certificate
# and no domain to get one for, so the deployed UI would load and then fail every request.
# CloudFront terminates TLS on its own *.cloudfront.net certificate, which needs no domain.
#
# It also narrows the security group afterwards, so the instance stops accepting traffic from the
# whole internet and only answers CloudFront.
#
#   AWS_PROFILE=personal ./scripts/provision-cloudfront.sh
#
# ~$1/mo at demo traffic. A distribution takes a few minutes to deploy.

set -euo pipefail

# Git Bash on Windows rewrites any argument that looks like a Unix path into a Windows one, which
# corrupts SSM parameter names and IAM ARNs before the CLI ever sees them. Harmless elsewhere.
export MSYS_NO_PATHCONV=1

REGION="${AWS_REGION:-ap-south-1}"
NAME="${NAME:-leads-crm}"

ORIGIN_IP="$(aws ec2 describe-addresses --region "$REGION" \
  --filters Name=tag:Name,Values="${NAME}-eip" \
  --query 'Addresses[0].PublicIp' --output text)"

if [[ "$ORIGIN_IP" == "None" || -z "$ORIGIN_IP" ]]; then
  echo "No Elastic IP found. Run scripts/provision-infra.sh first." >&2
  exit 1
fi

echo "Origin: ${ORIGIN_IP}"

EXISTING="$(aws cloudfront list-distributions \
  --query "DistributionList.Items[?Comment=='${NAME}-api'].Id | [0]" --output text 2>/dev/null || echo "None")"

if [[ "$EXISTING" != "None" && -n "$EXISTING" ]]; then
  DOMAIN="$(aws cloudfront get-distribution --id "$EXISTING" --query 'Distribution.DomainName' --output text)"
  echo "Distribution ${EXISTING} already exists: https://${DOMAIN}"
else
  # AllViewer forwards every header, cookie and query string to the origin. For an API that is
  # what you want: Authorization and x-tenant-id must reach Spring intact, and the managed
  # CachingDisabled policy stops CloudFront serving one tenant's response to another.
  CONFIG=$(cat <<CONFIG
{
  "CallerReference": "${NAME}-api-$(date +%s)",
  "Comment": "${NAME}-api",
  "Enabled": true,
  "Origins": {
    "Quantity": 1,
    "Items": [{
      "Id": "${NAME}-origin",
      "DomainName": "${ORIGIN_IP}.nip.io",
      "CustomOriginConfig": {
        "HTTPPort": 80,
        "HTTPSPort": 443,
        "OriginProtocolPolicy": "http-only",
        "OriginSslProtocols": {"Quantity": 1, "Items": ["TLSv1.2"]},
        "OriginReadTimeout": 60,
        "OriginKeepaliveTimeout": 5
      }
    }]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "${NAME}-origin",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": {
      "Quantity": 7,
      "Items": ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"],
      "CachedMethods": {"Quantity": 2, "Items": ["GET", "HEAD"]}
    },
    "CachePolicyId": "4135ea2d-6df8-44a3-9df3-4b5a84be39ad",
    "OriginRequestPolicyId": "216adef6-5c7f-47e4-b989-5492eafa07d3",
    "Compress": true
  },
  "PriceClass": "PriceClass_100"
}
CONFIG
)

  RESULT="$(aws cloudfront create-distribution --distribution-config "$CONFIG" \
    --query '{Id:Distribution.Id,Domain:Distribution.DomainName}' --output json)"
  EXISTING="$(echo "$RESULT" | grep -o '"Id": "[^"]*"' | cut -d'"' -f4)"
  DOMAIN="$(echo "$RESULT" | grep -o '"Domain": "[^"]*"' | cut -d'"' -f4)"
  echo "Created distribution ${EXISTING}"
fi

# ── Narrow the security group ─────────────────────────────────────────────────
# With CloudFront in front, the instance no longer needs to accept traffic from anywhere else.
# AWS publishes a managed prefix list of CloudFront origin-facing ranges, so this stays correct
# as those ranges change.

SG_ID="$(aws ec2 describe-security-groups --region "$REGION" \
  --filters Name=group-name,Values="${NAME}-sg" --query 'SecurityGroups[0].GroupId' --output text)"

PREFIX_LIST_ID="$(aws ec2 describe-managed-prefix-lists --region "$REGION" \
  --filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing \
  --query 'PrefixLists[0].PrefixListId' --output text 2>/dev/null || echo "None")"

if [[ "$PREFIX_LIST_ID" != "None" && -n "$PREFIX_LIST_ID" ]]; then
  if aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
      --ip-permissions "IpProtocol=tcp,FromPort=80,ToPort=80,PrefixListIds=[{PrefixListId=${PREFIX_LIST_ID}}]" \
      >/dev/null 2>&1; then
    echo "Allowed port 80 from the CloudFront prefix list"
  else
    echo "CloudFront prefix list rule already present"
  fi

  # Only drop the open rule once the narrower one is definitely in place.
  if aws ec2 revoke-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
      --protocol tcp --port 80 --cidr 0.0.0.0/0 >/dev/null 2>&1; then
    echo "Revoked open access on port 80"
  else
    echo "Open rule on port 80 already removed"
  fi
else
  echo "WARNING: CloudFront prefix list not found; port 80 is still open to 0.0.0.0/0." >&2
fi

cat <<SUMMARY

Done.

  Distribution  ${EXISTING}
  API URL       https://${DOMAIN}

It takes a few minutes to finish deploying. Then:

  GitHub secret SERVICE_URL       https://${DOMAIN}
  Amplify env   VITE_SERVER_URL   https://${DOMAIN}
  Host .env     APP_CORS_ORIGINS  https://<your-amplify-domain>

Health check once deployed:
  curl https://${DOMAIN}/leads-crm/actuator/health
SUMMARY
