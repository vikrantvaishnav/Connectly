# Connectly — run doc

Two processes: Spring Boot API (:8080) + Vite dev server (:5173, proxies `/api` → 8080).

## Reproduce artifacts

- **`.env` (backend)**: copy `.env.example` → `backend/.env` if it does not exist. Dev values are placeholders — no secrets needed locally.
- **Dependencies**: `cd backend && ./mvnw -B -q -DskipTests compile` (first run downloads Maven deps); `cd frontend && npm install` (first run only).
- No env files are copied from a main checkout — the dev profile (`application-dev.yml`) uses in-memory H2 with no credentials, and log-mode email. Everything needed is in the repo.

## Run servers

### Mode A — local dev (in-memory H2, data resets on restart)

Backend (log: `/tmp/connectly-backend.log`):

```bash
{ nohup bash -c 'cd backend && exec ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev' > /tmp/connectly-backend.log 2>&1 < /dev/null & echo "pid=$!"; disown; }
```

Frontend (log: `.freebuff/preview-bf1f570a-eabe-4709-8ec9-49da53be5520.log`):

```bash
{ nohup bash -c 'cd frontend && exec npm run dev' > .freebuff/preview-bf1f570a-eabe-4709-8ec9-49da53be5520.log 2>&1 < /dev/null & echo "pid=$!"; disown; }
```

Readiness: poll `curl -s http://localhost:8080/api/v1/health` (backend, ~20–40s on first run) and `curl -s http://localhost:5173/` (frontend, ~3–10s).

### Mode B — cloud database (Aiven MySQL, data persists across restarts)

1. Fill in `backend/.env` (git-ignored; template: `backend/.env.cloud.example`) with DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD from the provider's connection panel.
2. `bash backend/scripts/run-cloud.sh` (foreground) — or detached:

```bash
bash -c 'setsid nohup bash -c "cd backend && exec ./scripts/run-cloud.sh" >> /tmp/connectly-cloud.log 2>&1 < /dev/null &'
```

Flyway applies migrations V1–V4 to the cloud DB on first boot; later boots just validate. Verified live: user + post created, backend fully stopped and restarted, data read back intact.

Notes:
- Dev DB is in-memory H2 — all data resets when the backend restarts (expected in dev).
- Verification/reset emails print to the backend log; grab links with `grep -E "verify-email|reset-password" /tmp/connectly-backend.log | tail`.
- Smokes: `bash backend/scripts/auth-e2e.sh`, `bash backend/scripts/nearby-e2e.sh`.
