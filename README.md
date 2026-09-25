# Connectly

Nearby people + social feed + communities + real-time chat.

A full-stack platform combining Instagram-style social networking, Discord-style
communities, and dating-app-style discovery — all in one place.

## What's inside

**Dating layer**

- **Discover deck** (`/discover`) — people near you ranked by shared interests
  and distance, with people you've already matched with excluded.
- **Requests & Matches** (`/requests`) — Accept/Decline incoming connect
  requests, see requests you've sent, and turn matches into chats with "Say hi".
- **Rich profiles** — photo, interests, an intent line, and a server-derived
  18+ age (the age is never trusted from the client).
- **Mutual-consent connections** — a request only becomes a match when both
  sides act on it; nobody gets an unsolicited chat.

**Discord layer**

- Communities with owner/admin/member roles and text channels.
- **Typing indicators**, **emoji reactions**, and **"active now" presence** in
  direct messages, plus WebRTC voice rooms.
- Live delivery over STOMP/WebSocket; REST stays the authority for every
  mutation, so a forged push can only ever trigger a refetch.

**Privacy invariants** (see `NearbyService`)

- Raw coordinates are never returned — only distances rounded to ~100 m.
- `discoverable = false` hides you from Nearby and Discover entirely.
- Search radius is server-clamped to 50 km; locations older than 7 days are
  treated as unknown and hidden.

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

## Key API surface

All routes are `/api/v1`; everything requires a bearer token unless noted.

| Method | Path                              | Description                                             |
| ------ | --------------------------------- | ------------------------------------------------------- |
| GET    | `/health`                         | Liveness + service name (public)                        |
| GET    | `/posts/{id}`                     | Public post, still 404s on FOLLOWERS/PRIVATE visibility  |
| GET    | `/nearby`                         | People within a radius — rounded distance only          |
| GET    | `/discover/suggestions`           | Ranked discovery deck (shared interests + proximity)    |
| GET    | `/connections/summary`            | Badge counts: incoming / outgoing / matches             |
| POST   | `/connections/{userId}`           | Send a request, or accept one they sent you             |
| POST   | `/conversations/{id}/typing`      | Ephemeral typing ping (relayed, never stored)           |
| POST   | `/messages/{id}/reactions`        | Toggle an emoji reaction; returns updated tallies       |
| GET    | `/presence`                       | How many people are active right now                    |
| POST   | `/media`                          | Image upload (magic-byte sniffed, 5 MB cap)             |

## Roadmap

- [x] **Phase 1–10** — Foundation, auth, social graph, nearby, messaging, communities, voice, hardening, production
- [x] **Phase 11 — Social/dating pass**: rich profiles, Discover deck, Requests & Matches,
      typing indicators, message reactions and presence; batched read paths throughout
- [ ] **Next**: Render disk for persistent uploads, push notifications, message threads,
      admin/moderation tooling, Redis-backed presence for multi-instance deploys

## Conventions

- API prefix: `/api/v1`
- Errors: `{ timestamp, status, error, message, path }` — no stack traces
- Secrets come from environment variables only; nothing is hard-coded
- Schema changes go through Flyway migrations (`backend/src/main/resources/db/migration`)
