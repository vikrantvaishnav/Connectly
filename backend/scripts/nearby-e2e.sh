#!/usr/bin/env bash
# Live exercise of the Phase 3 nearby flows against the running dev backend.
set -u
BASE="${BASE:-http://localhost:8080/api/v1}"
LOG="${LOG:-/tmp/connectly-backend.log}"
TS=$(date +%s)
jq_() { python3 -c "import json,sys;d=json.load(open(sys.argv[2]));print(eval('d'+sys.argv[1]))" "$1" "$2" 2>/dev/null; }

reg() { # email username
  curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
    -d "{\"firstName\":\"$2\",\"lastName\":\"Near\",\"username\":\"$2$TS\",\"email\":\"$1\",\"password\":\"warm-lantern-cider-$TS\"}" -o /dev/null
}
verify_latest() {
  local T=$(grep -oE "verify-email/[A-Za-z0-9._~-]+" "$LOG" | tail -1 | cut -d/ -f2)
  curl -s -X POST "$BASE/auth/verify-email" -H 'Content-Type: application/json' -d "{\"token\":\"$T\"}" -o /dev/null
}
login() { # email -> echoes token
  curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
    -d "{\"identifier\":\"$1\",\"password\":\"warm-lantern-cider-$TS\"}" | python3 -c "import json,sys;print(json.load(sys.stdin)['accessToken'])"
}

E1="riya$TS@test.in"; reg "$E1" riya; verify_latest
E2="omkar$TS@test.in"; reg "$E2" omkar; verify_latest
E3="sia$TS@test.in"; reg "$E3" sia; verify_latest

T1=$(login "$E1"); T2=$(login "$E2"); T3=$(login "$E3")
loc() { curl -s -X PUT "$BASE/users/me/location" -H "Authorization: Bearer $1" \
  -H 'Content-Type: application/json' -d "{\"latitude\":$2,\"longitude\":$3,\"discoverable\":true}" -o /dev/null -w '%{http_code}'; }

echo "share locations: riya(19.0760,72.8777)=$(loc "$T1" 19.0760 72.8777) omkar(19.0550,72.9000)=$(loc "$T2" 19.0550 72.9000) sia(18.9400,72.8350)=$(loc "$T3" 18.9400 72.8350)"

echo "-- riya searches 20 km (expect omkar ~3.3 km, sia ~15.8 km, no coordinate fields) --"
curl -s "$BASE/nearby?lat=19.0760&lng=72.8777&radiusKm=20" -H "Authorization: Bearer $T1" \
  | python3 -c "import json,sys;[print(h['username'], h['distanceKm'], sorted(h.keys())) for h in json.load(sys.stdin)]"

echo "-- 5 km radius (expect only omkar) --"
curl -s "$BASE/nearby?lat=19.0760&lng=72.8777&radiusKm=5" -H "Authorization: Bearer $T1" \
  | python3 -c "import json,sys;print([h['username'] for h in json.load(sys.stdin)])"

echo "-- sia hides, search again (expect only omkar) --"
curl -s -X PUT "$BASE/users/me/discoverability" -H "Authorization: Bearer $T3" \
  -H 'Content-Type: application/json' -d '{"discoverable":false}' -o /dev/null
curl -s "$BASE/nearby?lat=19.0760&lng=72.8777&radiusKm=20" -H "Authorization: Bearer $T1" \
  | python3 -c "import json,sys;print([h['username'] for h in json.load(sys.stdin)])"

echo "-- absurd radius rejected (expect 400) --"
curl -s -o /dev/null -w '%{http_code}\n' "$BASE/nearby?lat=19.07&lng=72.87&radiusKm=999" -H "Authorization: Bearer $T1"
echo "-- status endpoint --"
curl -s "$BASE/users/me/location-status" -H "Authorization: Bearer $T1"; echo
