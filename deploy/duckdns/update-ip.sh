#!/usr/bin/env bash
set -euo pipefail

: "${DUCKDNS_TOKEN:?DUCKDNS_TOKEN must be set in the systemd EnvironmentFile}"
DOMAIN="zipchatgoso"
METADATA="http://169.254.169.254/latest"

TOKEN=$(curl --fail --silent --show-error --connect-timeout 2 --max-time 5 \
  -X PUT "$METADATA/api/token" \
  -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')
PUBLIC_IP=$(curl --fail --silent --show-error --connect-timeout 2 --max-time 5 \
  -H "X-aws-ec2-metadata-token: $TOKEN" \
  "$METADATA/meta-data/public-ipv4")

if [[ ! "$PUBLIC_IP" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
  echo 'EC2 metadata returned an invalid IPv4 address' >&2
  exit 1
fi

RESULT=$(curl --fail --silent --show-error --connect-timeout 5 --max-time 15 \
  --get 'https://www.duckdns.org/update' \
  --data-urlencode "domains=$DOMAIN" \
  --data-urlencode "token=$DUCKDNS_TOKEN" \
  --data-urlencode "ip=$PUBLIC_IP")

if [[ "$RESULT" != 'OK' ]]; then
  echo "DuckDNS update failed for $DOMAIN (response: $RESULT)" >&2
  exit 1
fi

echo "DuckDNS updated $DOMAIN to $PUBLIC_IP"
