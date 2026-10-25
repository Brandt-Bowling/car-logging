#!/usr/bin/env bash
set -e

# ==============================================================================
# One-Time Tesla Fleet API Partner Registration & Firebase Hosting Deploy
# ==============================================================================
# Reads TESLA_CLIENT_ID and TESLA_CLIENT_SECRET from git-ignored local.properties
# (or accepts them as CLI args).
# ==============================================================================

PROP_FILE="local.properties"
get_prop() {
  if [ -f "$PROP_FILE" ]; then
    grep "^$1=" "$PROP_FILE" | head -n 1 | cut -d'=' -f2- | tr -d '[:space:]"'
  fi
}

CLIENT_ID="${1:-$(get_prop TESLA_CLIENT_ID)}"
CLIENT_SECRET="${2:-$(get_prop TESLA_CLIENT_SECRET)}"
DOMAIN="car-logger-track.web.app"

echo "[1/3] Deploying public key & OAuth redirect bridge to Firebase Hosting (${DOMAIN})..."
firebase deploy --only hosting

if [ -z "$CLIENT_ID" ] || [ -z "$CLIENT_SECRET" ]; then
  echo ""
  echo "Hosting deployed to https://${DOMAIN}!"
  echo "Public key live at: https://${DOMAIN}/.well-known/appspecific/com.tesla.3p.public-key.pem"
  echo "Add TESLA_CLIENT_ID and TESLA_CLIENT_SECRET to local.properties and re-run ./scripts/setup_tesla_partner.sh"
  exit 0
fi

echo "[2/3] Requesting Partner Authentication Token from Tesla..."
PARTNER_TOKEN=$(curl -s -X POST "https://auth.tesla.com/oauth2/v3/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" \
  --data-urlencode "scope=openid vehicle_device_data" \
  --data-urlencode "audience=https://fleet-api.prd.na.vn.cloud.tesla.com" | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))")

if [ -z "$PARTNER_TOKEN" ]; then
  echo "Error: Failed to obtain partner token from Tesla."
  exit 1
fi

echo "[3/3] Registering ${DOMAIN} with Tesla North America Fleet API..."
curl -s -X POST "https://fleet-api.prd.na.vn.cloud.tesla.com/api/1/partner_accounts" \
  -H "Authorization: Bearer ${PARTNER_TOKEN}" \
  -H "Content-Type: application/json" \
  --data "{\"domain\": \"${DOMAIN}\"}"
echo ""
echo "Done! Tesla Fleet API is now active for ${DOMAIN}."
