#!/usr/bin/env bash
# Runs at every EC2 boot: reads the instance's current public IP and points the
# GitHub webhook at it, creating the webhook if it does not exist yet.
set -euo pipefail

GITHUB_REPO="${GITHUB_REPO:-zestabhijeet/SonarQubeCoverageJava}"
JENKINS_PORT="${JENKINS_PORT:-8080}"
TOKEN_FILE="${TOKEN_FILE:-/etc/jenkins-webhook/github-token}"   # fine-grained PAT, Webhooks: read & write

GH_TOKEN="$(tr -d '[:space:]' < "$TOKEN_FILE")"
IMDS="http://169.254.169.254/latest"

# 1. Current public IP from EC2 instance metadata (IMDSv2); retry while networking comes up
IP=""
for _ in $(seq 1 30); do
  IMDS_TOKEN="$(curl -sf -X PUT "$IMDS/api/token" -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' || true)"
  IP="$(curl -sf -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" "$IMDS/meta-data/public-ipv4" || true)"
  [ -n "$IP" ] && break
  sleep 2
done
[ -n "$IP" ] || { echo "Could not read public IP from instance metadata" >&2; exit 1; }

WEBHOOK_URL="http://${IP}:${JENKINS_PORT}/github-webhook/"
API="https://api.github.com/repos/${GITHUB_REPO}/hooks"
gh() { curl -sf -H "Authorization: Bearer ${GH_TOKEN}" -H 'Accept: application/vnd.github+json' \
            -H 'X-GitHub-Api-Version: 2022-11-28' "$@"; }

# 2. Find the existing Jenkins webhook (any hook whose URL ends in /github-webhook/)
HOOK_ID="$(gh "$API" | jq -r '[.[] | select(.config.url | test("/github-webhook/$"))][0].id // empty')"

PAYLOAD="$(jq -n --arg url "$WEBHOOK_URL" \
  '{active: true, events: ["push"],
    config: {url: $url, content_type: "json", insecure_ssl: "1"}}')"

# 3. Update it, or create it the first time
if [ -n "$HOOK_ID" ]; then
  gh -X PATCH "$API/$HOOK_ID" -d "$PAYLOAD" > /dev/null
  echo "Updated webhook $HOOK_ID -> $WEBHOOK_URL"
else
  gh -X POST "$API" -d "$(echo "$PAYLOAD" | jq '. + {name: "web"}')" > /dev/null
  echo "Created webhook -> $WEBHOOK_URL"
fi
