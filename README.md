# Connectly

Nearby people + social feed + communities + real-time chat.

A full-stack platform combining Instagram-style social networking, Discord-style
communities, and nearby-person discovery.

## Tech stack

| Layer     | Technology                                                                 |
| --------- | -------------------------------------------------------------------------- |
| Frontend  | React 19, TypeScript, Vite, Tailwind CSS 4, React Router, TanStack Query, Zustand, Axios |
| Backend   | Java 21, Spring Boot 4, Spring Security, Spring Data JPA, Flyway, Maven    |
| Database  | MySQL 8 (H2 in MySQL mode for tests)                                       |
| Cache/RT  | Redis (used from Phase 5 onward)                                           |
| Infra     | Docker, Docker Compose, nginx                                              |

## Quick start

### Full stack with Docker

```bash
cp .env.example .env      # adjust credentials first
docker compose up --build
```

| Service  | URL                        |
| -------- | -------------------------- |
| Frontend | http://localhost:3000      |
| API      | http://localhost:8080/api/v1/health |
| MySQL    | localhost:3306             |
| Redis    | localhost:6379             |

### Backend only (dev)

```bash
cd backend
./mvnw spring-boot:run    # requires MySQL running locally
./mvnw test               # runs on in-memory H2 (MySQL mode), no Docker needed
```

### Frontend only (dev)

```bash
cd frontend
npm install
npm run dev               # http://localhost:5173, proxies-free; talks to :8080 directly
```

## Project layout

```
backend/    Spring Boot API (Java 21, Maven wrapper included)
frontend/   React SPA (Vite + Tailwind + TanStack Query + Zustand)
docker-compose.yml    Full local stack: mysql, redis, backend, frontend
.env.example          Environment variables (never commit the real .env)
```

## API surface (Phase 1)

| Method | Path                 | Auth | Description                |
| ------ | -------------------- | ---- | -------------------------- |
| GET    | `/api/v1/health`     | No   | Liveness + service name    |
| GET    | `/actuator/health`   | No   | Actuator health probes     |

All other endpoints are locked down until JWT authentication lands in Phase 2.

## Roadmap

- [x] **Phase 1 — Foundation**: scaffolds, Docker Compose, MySQL schema v1, health check
- [ ] **Phase 2 — Auth & profiles**: register/login, JWT access + refresh, BCrypt, user profiles
- [ ] **Phase 3 — Social**: posts, likes, comments, follow, connections, feed, search
- [ ] **Phase 4 — Nearby**: location capture, spatial queries, discoverability, privacy controls
- [ ] **Phase 5 — Messaging**: conversations, WebSocket, presence, typing, notifications
- [ ] **Phase 6 — Communities**: servers, text/voice channels, roles, permissions
- [ ] **Phase 7 — Voice/video**: WebRTC signaling, voice rooms
- [ ] **Phase 8 — Admin & moderation**: reports, bans, audit logs
- [ ] **Phase 9 — Hardening**: rate limiting, file uploads (S3), performance, full test matrix
- [ ] **Phase 10 — Production**: CI/CD, HTTPS, monitoring, backups

## Conventions

- API prefix: `/api/v1`
- Errors: `{ timestamp, status, error, message, path }` — no stack traces
- Secrets come from environment variables only; nothing is hard-coded
- Schema changes go through Flyway migrations (`backend/src/main/resources/db/migration`)
