#!/usr/bin/env bash
set -euo pipefail

BASE_URL="http://127.0.0.1:8081"
WORKSPACE_ROOT="../../../sample-workspaces/tiny"

curl -sS -X POST "$BASE_URL/api/workspace/open" \
  -H "Content-Type: application/json" \
  --data-raw "{\"rootPath\":\"$WORKSPACE_ROOT\"}"
echo
