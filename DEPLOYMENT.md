# Connectly — Production Deployment Guide (Phase 8)

Everything is built; this gets it live on the internet using free tiers.

## Architecture

```
Browser ──HTTPS──> Frontend (static on Vercel) ──proxy /api /ws /media──> Backend (Spring Boot on Render) ──TLS──> Aiven MySQL (already live)
```

The frontend is a single origin: rewrites proxy API, WebSocket and media to the backend, so there is no CORS in production.

## What's already prepared (in the repo)

- `backend/Dockerfile` — multi-stage Maven build, non-root user
- `frontend/Dockerfile` + `frontend/nginx.conf` — build + nginx with /api, /ws, /media proxies and SPA fallback
- `docker-compose.yml` — local full-stack test
- `backend/.env` — your Aiven credentials (git-ignored; the same values go into the host's env vars)
- `frontend/vercel.json` — rewrite config template (fill in your backend URL)

## Step 1 — Free accounts (you create them, never share passwords)

1. **Render** (render.com) — hosts the backend. Free tier: 750 hrs/month, sleeps after 15 min idle (~50 s wake).
2. **Vercel** (vercel.com) or **Netlify** — hosts the static frontend (always fast, no sleeping).
3. Push the repo to **GitHub** (if not already) — both platforms deploy from Git.

Optional later: a domain (~$10/yr) + Cloudflare free tier for HTTPS/WAF/DDoS.

## Step 2 — Backend on Render

1. New → Web Service → connect the repo → **Docker** runtime, root directory `backend/`.
2. Instance: Free.
3. Environment variables (from your Aiven connection panel + secrets you generate):
   - `SPRING_PROFILES_ACTIVE=cloud`
   - `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` (same values as backend/.env)
   - `JWT_SECRET` — generate 64 random bytes: `openssl rand -hex 64` (never reuse the dev one)
   - `CORS_ALLOWED_ORIGINS=https://your-frontend.vercel.app`
   - `MAIL_PUBLIC_BASE_URL=https://your-frontend.vercel.app`
   - `MAIL_MODE=log` (emails print to logs; switch to `smtp` + `MAIL_HOST/PORT/USERNAME/PASSWORD/FROM` with a free Brevo/Gmail app-password when you want real emails)
   - `APP_MEDIA_DIR=/var/data/media` — after first deploy, add a Render **Disk** (1 GB free) mounted there so uploads survive redeploys.
4. Health check path: `/api/v1/health`.
5. Deploy — Flyway migrates the Aiven DB automatically on boot (schema is already at v8, so it will just validate).

## Step 3 — Frontend on Vercel/Netlify

1. New project → repo → root directory `frontend/` (framework: Vite; build `npm run build`, output `dist/`).
2. Add a rewrite/proxy so the single-origin trick works without nginx:
   - **Vercel** — `frontend/vercel.json` is provided; replace `YOUR-BACKEND` with the Render URL:
     ```json
     { "rewrites": [
       { "source": "/api/(.*)", "destination": "https://YOUR-BACKEND.onrender.com/api/$1" },
       { "source": "/ws/(.*)", "destination": "https://YOUR-BACKEND.onrender.com/ws/$1" },
       { "source": "/media/(.*)", "destination": "https://YOUR-BACKEND.onrender.com/media/$1" }
     ] }
     ```
     (WebSockets are proxied automatically.)
   - **Netlify** equivalent: `frontend/public/_redirects` with
     `/api/* https://YOUR-BACKEND.onrender.com/api/:splat 200` plus the same for `/ws/*` and `/media/*`.
3. Commit and deploy.

## Step 4 — Verify live

1. `https://your-frontend.vercel.app/api/v1/health` → `{"status":"UP"}`
2. Register with a real email (verification link appears in Render → Logs while MAIL_MODE=log)
3. Post, like, chat (WebSocket), check nearby, join a voice room
4. Re-run `bash backend/scripts/auth-e2e.sh` pointed at the live URL for a full smoke test

## Free-tier realities (plan for them)

| Thing | Free behavior | Mitigation |
|---|---|---|
| Render sleeps after 15 min idle | ~50 s cold start | A cron ping (`https://…/api/v1/health` every 10 min) keeps it warm |
| WebSocket + sleeping backend | Sockets drop on spin-down | Reconnect logic already built into the frontend (3 s retry) |
| Aiven free DB | 1 GB — plenty for now | Upgrade path is just the connection URL |
| Uploads | 1 GB Render disk | Switch `MediaService` to S3 later (single class) |

## Security checklist before sharing publicly

- [ ] `JWT_SECRET` is a fresh 64-byte random value, only in Render env
- [ ] `CORS_ALLOWED_ORIGINS` lists only your frontend URL
- [ ] Tighten rate limits in `application-cloud.yml` back to production values if you expect real traffic
- [ ] Aiven: enable IP allowlist for Render's outbound IPs if available on the free tier
- [ ] Set `MAIL_MODE=smtp` with real credentials when you want real emails
- [ ] Google OAuth: add the live origins in Google Cloud Console and set `GOOGLE_OAUTH_ENABLED=true` + client id/secret
