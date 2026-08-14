#!/bin/sh
set -eu

base_url="${1:-http://127.0.0.1:8080}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

curl --fail --silent --show-error --dump-header "$work_dir/index.headers" \
    --output "$work_dir/index.html" "$base_url/"
grep -q "HomeLab Monitor" "$work_dir/index.html"
grep -qi '^Content-Security-Policy:' "$work_dir/index.headers"
grep -qi '^Strict-Transport-Security:' "$work_dir/index.headers"
grep -qi '^X-Content-Type-Options: nosniff' "$work_dir/index.headers"

curl --fail --silent --show-error "$base_url/actuator/health" > "$work_dir/health.json"
grep -q '"status":"UP"' "$work_dir/health.json"

curl --fail --silent --show-error "$base_url/api/v1/auth/status" > "$work_dir/auth.json"
grep -q '"authenticated":false' "$work_dir/auth.json"

protected_status="$(curl --silent --show-error --output /dev/null \
    --write-out '%{http_code}' "$base_url/api/v1/monitors")"
test "$protected_status" = "401"

curl --fail --silent --show-error --dump-header "$work_dir/csrf.headers" \
    --output /dev/null "$base_url/api/v1/csrf"
grep -Eqi '^Set-Cookie: .*Secure;.*HttpOnly;.*SameSite=Lax' "$work_dir/csrf.headers"

printf 'Production smoke checks passed for %s\n' "$base_url"
