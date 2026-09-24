#!/usr/bin/env bash
# One-off cleanup: delete every post owned by the demo users (via owner auth).
set -u
BASE=http://localhost:8080/api/v1
PASS='Demo-Passphrase-1!'
# username:email-prefix pairs (emails are prefix@connectly.demo)
PAIRS="vikrant_dev:vikrant meera_ux:meera rahul_fit:rahul sana_eats:sana arjun_music:arjun priya_codes:priya"

for pair in $PAIRS; do
  u="${pair%%:*}"
  email="${pair#*:}"
  payload=$(printf '{"identifier":"%s@connectly.demo","password":"%s"}' "$email" "$PASS")
  login=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "$payload")
  T=$(printf '%s' "$login" | grep -oE '"accessToken":"[^"]+"' | cut -d'"' -f4)
  if [ -z "$T" ]; then echo "$u: LOGIN FAILED"; continue; fi
  # post ids: the top-level object of each post starts with {"id":N,"author"
  ids=$(curl -s "$BASE/posts/users/$u?size=50" -H "Authorization: Bearer $T" \
        | grep -oE '\{"id":[0-9]+,"author"' | grep -oE '[0-9]+' | tr '\n' ' ')
  count=0
  for id in $ids; do
    code=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE/posts/$id" -H "Authorization: Bearer $T")
    count=$((count+1))
  done
  echo "$u: deleted $count posts"
done
