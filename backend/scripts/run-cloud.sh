#!/usr/bin/env bash
# Launch the Connectly backend against the cloud MySQL database (e.g. Aiven).
#
# Usage:  bash backend/scripts/run-cloud.sh
#
# Credentials live in backend/.env (git-ignored) — see backend/.env.cloud.example
# for the exact variables expected here. The file must contain at minimum:
#   DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD
set -euo pipefail

cd "$(dirname "$0")/.."   # backend/ (where pom.xml lives)

ENV_FILE=".env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: $ENV_FILE not found."
  echo "Copy backend/.env.cloud.example to backend/.env and fill in your database credentials."
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

: "${DB_HOST:?DB_HOST missing in backend/.env}"
: "${DB_NAME:?DB_NAME missing in backend/.env}"
: "${DB_USERNAME:?DB_USERNAME missing in backend/.env}"
: "${DB_PASSWORD:?DB_PASSWORD missing in backend/.env}"
DB_PORT="${DB_PORT:-3306}"

echo "→ Starting Connectly backend with cloud profile (db: ${DB_NAME} @ ${DB_HOST}:${DB_PORT})"
exec ./mvnw -q spring-boot:run \
  -Dspring-boot.run.profiles=cloud \
  -Dspring-boot.run.jvmArguments="-Xms256m -Xmx512m"
