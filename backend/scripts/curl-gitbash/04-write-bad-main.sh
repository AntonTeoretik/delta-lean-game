#!/usr/bin/env bash
set -euo pipefail

BASE_URL="http://127.0.0.1:8081"

curl -sS -X PUT "$BASE_URL/api/files/Main.lean" \
  -H "Content-Type: application/json" \
  --data-raw '{"content":"theorem bad : Prop := by\n  exact\n"}'
echo
