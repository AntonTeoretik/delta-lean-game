#!/usr/bin/env bash
set -euo pipefail

BASE_URL="http://127.0.0.1:8081"

curl -sS "$BASE_URL/api/diagnostics?path=Main.lean"
echo
