#!/usr/bin/env bash
# Seed demo data into the running Connectly backend (works on any profile).
# Idempotent: re-running skips users that already exist. Safe for the cloud DB.
set -u
BASE=http://localhost:8080/api/v1
LOG="${CONNECTLY_LOG:-/tmp/connectly-cloud.log}"
PASS='Demo-Passphrase-1!'
PASSCOUNT=0

jqv() { grep -oE "\"$1\":\"?[^\",]*\"?" | head -1 | sed 's/.*:"\?//;s/"\?$//'; }
countreg() { grep -c "AUDIT event=REGISTER" "$LOG" 2>/dev/null || true; }

reg() { # email username first last
  local email="$1" username="$2" first="$3" last="$4"
  local before after
  before=$(countreg)
  curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"username\":\"$username\",\"password\":\"$PASS\",\"firstName\":\"$first\",\"lastName\":\"$last\"}" >/dev/null
  after=$(countreg)
  if [ "${after:-0}" -gt "${before:-0}" ]; then
    # new user: verify email via the logged link
    local token
    token=$(grep -B4 "detail=username=$username" "$LOG" | grep -oE "verify-email/[A-Za-z0-9_-]+" | tail -1 | sed 's|.*/||')
    curl -s -X POST "$BASE/auth/verify-email" -H 'Content-Type: application/json' -d "{\"token\":\"$token\"}" >/dev/null
    PASSCOUNT=$((PASSCOUNT+1))
  fi
  # login (fresh token each call; also proves the account works)
  curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
    -d "{\"identifier\":\"$email\",\"password\":\"$PASS\"}"
}

post() { # idempotent: skip if this user already has a post with the same content
  local existing
  existing=$(curl -s "$BASE/posts/users/$4?size=50" -H "Authorization: Bearer $1" | grep -cF "$2" || true)
  [ "${existing:-0}" -eq 0 ] && curl -s -X POST "$BASE/posts" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -d "{\"content\":\"$2\",\"visibility\":\"$3\"}" >/dev/null
}

like()  { # idempotent: only like if not already liked
  local liked
  liked=$(curl -s "$BASE/posts/$2" -H "Authorization: Bearer $1" | grep -oE '"likedByMe":(true|false)' | cut -d: -f2)
  [ "$liked" = "true" ] || curl -s -X POST "$BASE/posts/$2/like" -H "Authorization: Bearer $1" >/dev/null
}

follow(){ # idempotent: only follow if not already following
  local following
  following=$(curl -s "$BASE/users/$3" -H "Authorization: Bearer $1" | grep -oE '"following":(true|false)' | cut -d: -f2)
  [ "$following" = "true" ] || curl -s -X POST "$BASE/users/$2/follow" -H "Authorization: Bearer $1" >/dev/null
}

loc()   { curl -s -X PUT "$BASE/users/me/location" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' -d "{\"latitude\":$2,\"longitude\":$3,\"discoverable\":true}"; }

echo "== Seeding Connectly demo data =="
before=$(countreg)

# ---- 6 demo users (name surname emoji-free) ----
A=$(reg vikrant@connectly.demo vikrant_dev Vikrant Sharma)
B=$(reg meera@connectly.demo meera_ux Meera Iyer)
C=$(reg rahul@connectly.demo rahul_fit Rahul Verma)
D=$(reg sana@connectly.demo sana_eats Sana Khan)
E=$(reg arjun@connectly.demo arjun_music Arjun Rao)
F=$(reg priya@connectly.demo priya_codes Priya Nair)

TA=$(echo "$A" | jqv accessToken)
TB=$(echo "$B" | jqv accessToken)
TC=$(echo "$C" | jqv accessToken)
TD=$(echo "$D" | jqv accessToken)
TE=$(echo "$E" | jqv accessToken)
TF=$(echo "$F" | jqv accessToken)

for t in "$TA" "$TB" "$TC" "$TD" "$TE" "$TF"; do
  [ -n "$t" ] || { echo "FATAL: a login failed - check backend log"; exit 1; }
done

# resolve real user ids from the live API (never assume numbering)
id_of() { curl -s "$BASE/auth/me" -H "Authorization: Bearer $1" | jqv id; }
IDA=$(id_of "$TA"); IDB=$(id_of "$TB"); IDC=$(id_of "$TC")
IDD=$(id_of "$TD"); IDE=$(id_of "$TE"); IDF=$(id_of "$TF")
echo "resolved ids: A=$IDA B=$IDB C=$IDC D=$IDD E=$IDE F=$IDF"

# ---- posts ----
post "$TA" "Shipped JWT refresh-token rotation today. Spring Security finally clicked for me 🎉" PUBLIC vikrant_dev >/dev/null
post "$TB" "New dark palette for our design system. Softer borders, calmer nights." PUBLIC meera_ux >/dev/null
post "$TC" "5am run along the promenade done. 21k in the bank. Who is in next Sunday?" PUBLIC rahul_fit >/dev/null
post "$TD" "The 7 best vada pavs within 2 km of the station. Number 7 will make you emotional." PUBLIC sana_eats >/dev/null
post "$TE" "Practicing for Friday's open-mic. Bringing the ukulele this time." PUBLIC arjun_music >/dev/null
post "$TF" "Hot take: most 10x developers are just people who read error messages properly." PUBLIC priya_codes >/dev/null
post "$TA" "Reading club this Sunday in the park - we discuss The Ministry of Time. DM to join." FOLLOWERS vikrant_dev >/dev/null

# ---- likes (organic-ish) ----
for t in "$TB" "$TC" "$TD" "$TE" "$TF"; do like "$t" 1; done
for t in "$TA" "$TC" "$TE"; do like "$t" 2; done
for t in "$TA" "$TB"; do like "$t" 4; done
like "$TA" 3; like "$TF" 3; like "$TB" 6; like "$TC" 6

# ---- follows (so the followers-only post has an audience) ----
# args: followerToken targetId targetUsername
follow "$TB" "$IDA" vikrant_dev; follow "$TC" "$IDA" vikrant_dev; follow "$TD" "$IDA" vikrant_dev
follow "$TA" "$IDB" meera_ux;   follow "$TE" "$IDB" meera_ux
follow "$TA" "$IDC" rahul_fit; follow "$TF" "$IDC" rahul_fit

# ---- nearby: Mumbai coordinates, spread across the city ----
loc "$TA" 19.1176 72.9060   # Vikrant - Powai
loc "$TB" 19.0760 72.8777   # Meera - Shivaji Park
loc "$TC" 19.0550 72.8310   # Rahul - Bandra
loc "$TD" 19.0176 72.8562   # Sana - Dadar
loc "$TE" 19.2183 72.9781   # Arjun - Thane
loc "$TF" 19.0330 73.0297   # Priya - Navi Mumbai

after=$(countreg)
echo "Created $(( ${after:-0} - ${before:-0} )) new users this run (skipped if they existed)."
echo "Demo logins (password for all): $PASS"
echo "  vikrant@connectly.demo / meera@connectly.demo / rahul@connectly.demo"
echo "  sana@connectly.demo / arjun@connectly.demo / priya@connectly.demo"
echo "== Done =="
