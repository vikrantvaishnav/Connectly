#!/usr/bin/env bash
# End-to-end exercise of the Connectly auth flows against a locally running dev backend.
# Usage: backend/scripts/auth-e2e.sh   (expects the backend up on :8080 with the dev profile)
set -u
BASE="${BASE:-http://localhost:8080/api/v1}"
LOG="${LOG:-/tmp/connectly-backend.log}"
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); echo "PASS: $1"; }
bad() { FAIL=$((FAIL+1)); echo "FAIL: $1"; }
code(){ curl -s -o /tmp/e2e-last.json -w '%{http_code}' "$@"; }
jq_() { python3 -c "import json,sys;d=json.load(open('/tmp/e2e-last.json'));print(eval('d'+sys.argv[1]))" "$1" 2>/dev/null; }

EMAIL="dev$(date +%s)@example.com"
USERNAME="dev$(date +%s | tail -c 6)"
PASSWORD="correct-horse-battery-9"
NEWPASSWORD="correct-horse-battery-42"

echo "== Account: $EMAIL =="

echo "-- register + email verification --"
S=$(code -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"firstName\":\"Dev\",\"lastName\":\"Tester\",\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
[ "$S" = "201" ] && ok "register → 201" || bad "register (got $S): $(cat /tmp/e2e-last.json)"

sleep 1
VTOKEN=$(grep -oE "verify-email/[A-Za-z0-9._~-]+" "$LOG" | tail -1 | cut -d/ -f2)
[ -n "${VTOKEN:-}" ] && ok "verification link found in mail log" || bad "no verification link in $LOG"
S=$(code -X POST "$BASE/auth/verify-email" -H 'Content-Type: application/json' -d "{\"token\":\"$VTOKEN\"}")
[ "$S" = "200" ] && ok "verify-email → 200" || bad "verify-email (got $S): $(cat /tmp/e2e-last.json)"
S=$(code -X POST "$BASE/auth/verify-email" -H 'Content-Type: application/json' -d "{\"token\":\"$VTOKEN\"}")
[ "$S" = "400" ] && ok "verify token single-use (replay → 400)" || bad "verify replay (got $S)"

echo "-- login + /me --"
S=$(code -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"identifier\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
ACCESS=$(jq_ "['accessToken']"); REFRESH=$(jq_ "['refreshToken']")
[ "$S" = "200" ] && [ -n "$ACCESS" ] && ok "login → tokens" || bad "login (got $S): $(cat /tmp/e2e-last.json)"
S=$(code "$BASE/auth/me" -H "Authorization: Bearer $ACCESS")
[ "$S" = "200" ] && [ "$(jq_ "['username']")" = "$USERNAME" ] && ok "/me returns the right user" || bad "/me (got $S)"

echo "-- refresh rotation + reuse detection --"
S=$(code -X POST "$BASE/auth/refresh" -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}")
NEWREFRESH=$(jq_ "['refreshToken']")
[ "$S" = "200" ] && [ -n "$NEWREFRESH" ] && [ "$NEWREFRESH" != "$REFRESH" ] && ok "refresh rotates the token" || bad "refresh rotation (got $S)"
S=$(code -X POST "$BASE/auth/refresh" -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}")
[ "$S" = "401" ] && ok "old refresh reuse → 401" || bad "refresh reuse (got $S)"
S=$(code -X POST "$BASE/auth/refresh" -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$NEWREFRESH\"}")
[ "$S" = "401" ] && ok "reuse revoked the whole session family" || bad "family revocation (got $S)"

echo "-- login again after revocation --"
S=$(code -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"identifier\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
ACCESS=$(jq_ "['accessToken']"); REFRESH=$(jq_ "['refreshToken']")
[ "$S" = "200" ] && ok "fresh login works" || bad "re-login (got $S)"

echo "-- forgot / reset password --"
S=$(code -X POST "$BASE/auth/forgot-password" -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\"}")
[ "$S" = "200" ] && ok "forgot-password → generic 200" || bad "forgot-password (got $S)"
sleep 1
RTOKEN=$(grep -oE "reset-password/[A-Za-z0-9._~-]+" "$LOG" | tail -1 | cut -d/ -f2)
[ -n "${RTOKEN:-}" ] && ok "reset link found in mail log" || bad "no reset link in $LOG"
S=$(code -X POST "$BASE/auth/reset-password" -H 'Content-Type: application/json' -d "{\"token\":\"$RTOKEN\",\"password\":\"$NEWPASSWORD\"}")
[ "$S" = "200" ] && ok "reset-password → 200" || bad "reset-password (got $S): $(cat /tmp/e2e-last.json)"
S=$(code -X POST "$BASE/auth/reset-password" -H 'Content-Type: application/json' -d "{\"token\":\"$RTOKEN\",\"password\":\"another-password-22\"}")
[ "$S" = "400" ] && ok "reset token single-use (replay → 400)" || bad "reset replay (got $S)"
S=$(code -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"identifier\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
[ "$S" = "401" ] && ok "old password rejected after reset" || bad "old password (got $S)"
S=$(code -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"identifier\":\"$EMAIL\",\"password\":\"$NEWPASSWORD\"}")
ACCESS=$(jq_ "['accessToken']")
[ "$S" = "200" ] && [ -n "$ACCESS" ] && ok "new password works" || bad "new-password login (got $S)"

echo "-- 2FA: TOTP + recovery codes --"
S=$(code -X POST "$BASE/auth/2fa/setup" -H "Authorization: Bearer $ACCESS")
SECRET=$(jq_ "['secret']")
[ "$S" = "200" ] && [ -n "$SECRET" ] && ok "2fa/setup → secret + otpauth URL" || bad "2fa/setup (got $S): $(cat /tmp/e2e-last.json)"
TOTP=$(python3 - "$SECRET" <<'PY'
import base64, hashlib, hmac, struct, sys, time
sec = sys.argv[1].replace(' ', '').upper()
key = base64.b32decode(sec + '=' * ((8 - len(sec) % 8) % 8))
counter = int(time.time()) // 30
h = hmac.new(key, struct.pack('>Q', counter), hashlib.sha1).digest()
o = h[19] & 0x0F
print('%06d' % ((struct.unpack('>I', h[o:o+4])[0] & 0x7FFFFFFF) % 10**6))
PY
)
S=$(code -X POST "$BASE/auth/2fa/enable" -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' -d "{\"code\":\"$TOTP\"}")
RECOVERY=$(jq_ "['codes'][0]")
[ "$S" = "200" ] && [ -n "$RECOVERY" ] && ok "2fa/enable with live TOTP → recovery codes" || bad "2fa/enable (got $S): $(cat /tmp/e2e-last.json)"

S=$(code -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"identifier\":\"$EMAIL\",\"password\":\"$NEWPASSWORD\"}")
MFATOKEN=$(jq_ "['mfaToken']")
[ "$S" = "200" ] && [ -n "$MFATOKEN" ] && ok "login now challenges for 2FA" || bad "2fa challenge (got $S): $(cat /tmp/e2e-last.json)"
TOTP2=$(python3 - "$SECRET" <<'PY'
import base64, hashlib, hmac, struct, sys, time
sec = sys.argv[1].replace(' ', '').upper()
key = base64.b32decode(sec + '=' * ((8 - len(sec) % 8) % 8))
counter = int(time.time()) // 30
h = hmac.new(key, struct.pack('>Q', counter), hashlib.sha1).digest()
o = h[19] & 0x0F
print('%06d' % ((struct.unpack('>I', h[o:o+4])[0] & 0x7FFFFFFF) % 10**6))
PY
)
S=$(code -X POST "$BASE/auth/mfa/verify" -H 'Content-Type: application/json' -d "{\"mfaToken\":\"$MFATOKEN\",\"code\":\"$TOTP2\"}")
[ "$S" = "200" ] && ok "mfa/verify with TOTP → tokens" || bad "mfa/verify TOTP (got $S): $(cat /tmp/e2e-last.json)"

S=$(code -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"identifier\":\"$EMAIL\",\"password\":\"$NEWPASSWORD\"}")
MFATOKEN=$(jq_ "['mfaToken']")
S=$(code -X POST "$BASE/auth/mfa/verify" -H 'Content-Type: application/json' -d "{\"mfaToken\":\"$MFATOKEN\",\"code\":\"$RECOVERY\"}")
[ "$S" = "200" ] && ok "recovery code works at mfa/verify" || bad "recovery code (got $S): $(cat /tmp/e2e-last.json)"

S=$(code -X POST "$BASE/auth/2fa/disable" -H "Authorization: Bearer $(jq_ "['accessToken']")" -H 'Content-Type: application/json' -d "{\"password\":\"$NEWPASSWORD\"}")
[ "$S" = "200" ] && ok "2fa/disable with password" || bad "2fa/disable (got $S): $(cat /tmp/e2e-last.json)"
S=$(code -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"identifier\":\"$EMAIL\",\"password\":\"$NEWPASSWORD\"}")
[ "$S" = "200" ] && ok "login is single-step again after disable" || bad "post-disable login (got $S)"

echo "-- lockout after repeated failures (account left locked; expected) --"
for i in 1 2 3 4 5; do
  code -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"identifier\":\"$EMAIL\",\"password\":\"totally-wrong-$i\"}" >/dev/null
done
S=$(code -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"identifier\":\"$EMAIL\",\"password\":\"$NEWPASSWORD\"}")
case "$S" in
  401|423|429) ok "correct password rejected while locked (HTTP $S)" ;;
  *) bad "lockout (got $S): $(cat /tmp/e2e-last.json)" ;;
esac

echo
echo "RESULT: $PASS passed, $FAIL failed"
[ "$FAIL" = "0" ]
