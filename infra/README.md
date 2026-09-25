# Connectly — Kong Gateway

Kong sits in front of the Connectly backend as the single public entrypoint:
routing, JWT verification, rate limiting, CORS, request-size protection and
correlation IDs all live at the edge. The backend is never exposed directly.

```
Browser / Mobile
      │  https
      ▼
┌──────────────┐
│ KONG GATEWAY │  :8000 HTTP · :8443 HTTPS   (Admin API 8001 — internal only)
└──────┬───────┘
       │  connetly-network (private Docker network)
       ▼
┌───────────────────────┐     ┌─────────┐
│ connectly-backend     │────▶│  MySQL  │  (private)
│ Spring Boot monolith  │     └─────────┘
│ :8080 (no public port)│     ┌─────────┐
│  • REST /api/v1/**    │     │  Redis  │  (private; for multi-node rate limits)
│  • WS   /ws/chat      │     └─────────┘
└───────────────────────┘
```

> **Reality check (read first):** Connectly is currently a **single Spring
> Boot monolith** — it has no internal gRPC services and no Kafka. Kong here
> fronts *one* upstream. That is the correct Stage-1 topology from the
> staging plan; when services are split, add one `service:` block per
> upstream in `kong.yml` — nothing else changes. See
> [ADR-001: Kafka & gRPC](#adr-001-kafka--grpc-decision) at the bottom.

## Quick start

```bash
cp .env.example .env          # then set JWT_SECRET etc. (never commit .env)
docker compose -f docker-compose.kong.yml up -d
docker compose -f docker-compose.kong.yml ps
docker compose -f docker-compose.kong.yml logs -f kong
```

Then:

```bash
# through the gateway
curl -i http://localhost:8000/api/v1/health
# direct backend — IMPOSSIBLE from outside the compose network (no published port)
curl -i http://localhost:8080/api/v1/health   # connection refused ✓
```

## Route table

| Route (public via Kong) | Upstream | Auth | Notes |
|---|---|---|---|
| `/api/v1/auth/*` | connectly-backend:8080 | public (register/login/verify-otp) + JWT | backend enforces which |
| `/api/v1/users/**`, `/api/v1/posts/**`, `/api/v1/chat/**`, … | connectly-backend:8080 | JWT at edge + business authz in app | app is the authorization authority |
| `/api/v1/admin/**` | connectly-backend:8080 | JWT + `role=ADMIN` in app | never rely on the edge alone |
| `/ws/chat` (STOMP) | connectly-backend:8080 | STOMP CONNECT token (in app) | WebSocket route, long timeouts, no retries |
| `/media/{name}` | connectly-backend:8080 | public read | generated names, size-limited uploads |

Kong verifies **token validity** (signature, exp, nbf). The application
verifies **authorization** (owner checks, roles, mutes/blocks). Both layers
are required.

## Configuration map

| File | Purpose |
|---|---|
| `infra/kong/kong.yml` | Declarative config (DB-less): services, routes, consumers, plugins — **source of truth, in Git** |
| `infra/kong/kong.conf` | Kong runtime settings (listeners, logs, DB-less) |
| `docker-compose.kong.yml` | Gateway topology: Kong + backend + MySQL + Redis on `connetly-network` |
| `.env.example` | Placeholder env vars (real `.env` is git-ignored) |

Secrets: the JWT secret reaches Kong via `KONG_VAULT_JWT_SECRET`
(`{vault://env/jwt-secret}` in `kong.yml`) — it is **never** committed and
**never** placed in Kong's database or the YAML itself.

## Plugins enabled (and why)

| Plugin | Why | Scope | Perf note |
|---|---|---|---|
| `jwt` | reject tampered/expired tokens at the edge, before they hit Java | global, pass-through (anonymous = connetly-public) so public endpoints stay public | cheap HMAC check |
| `cors` | one consistent CORS policy; no wildcard-with-credentials | global | negligible |
| `correlation-id` | `X-Request-Id` on every request for tracing | global | negligible |
| `request-size-limiting` | 5 MB body cap (DoS guard) | global | negligible |
| `rate-limiting` | 100 req/min/IP on the API route | route-scoped | `policy: local` (single node); switch to `redis` for multi-node |

Deliberately **not** enabled: bot detection, ACL, IP restriction — no
requirement for them yet; each plugin adds latency and config surface.

## Rate-limit categories

The gateway enforces the coarse limit (100/min/IP). Finer-grained limits
remain **in the application** where identity is known (`RateLimiter` in the
backend: login 10/60, register 5/300, forgot/reset 5/3600, verify-otp 10/300,
delete-account 10/3600). When services split, promote these to per-route
Kong limits.

## Testing

```bash
# 1. public endpoint through Kong → 200
curl -i http://localhost:8000/api/v1/health

# 2. protected endpoint without token → 401 (from the backend, or Kong when enforced)
curl -i http://localhost:8000/api/v1/auth/me

# 3. expired/garbage token → 401
curl -i http://localhost:8000/api/v1/auth/me -H "Authorization: Bearer garbage"

# 4. rate limit → 429 after 100 rapid calls
for i in $(seq 1 110); do curl -s -o /dev/null -w "%{http_code} " http://localhost:8000/api/v1/health; done; echo

# 5. oversized body → 413
head -c 6000000 /dev/zero | curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST http://localhost:8000/api/v1/posts -H "Content-Type: application/json" --data-binary @-

# 6. correlation id present → X-Request-Id header on any response
curl -si http://localhost:8000/api/v1/health | grep -i x-request-id

# 7. CORS preflight from allowed origin → 200 with ACAO header
curl -si -X OPTIONS http://localhost:8000/api/v1/auth/login \
  -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: POST" | head

# 8. admin API NOT public
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8001   # refused/timed out from outside host
```

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `502 Bad Gateway` | backend down/not on `connetly-network` | `docker compose -f docker-compose.kong.yml ps`, check `connectly-backend` health |
| `401` with valid token | Kong's JWT secret ≠ backend's | check `KONG_VAULT_JWT_SECRET` matches `JWT_SECRET`; `iss` claim must equal consumer key `connectly` |
| `429` immediately | rate-limit bucket shared by NAT/proxy IP | switch `policy` to `redis`, or raise route limit |
| `kong.yml` rejected at boot | declarative config invalid | `docker run --rm -v $PWD/infra/kong/kong.yml:/kong/declarative/kong.yml kong/kong-gateway:3.9.1.0 kong config db_import /kong/declarative/kong.yml` (or `kong config parse`) |
| CORS failures | origin missing | add origin to `kong.yml` cors plugin (or via env when templated) |
| WebSocket connects then drops | proxy buffering/timeouts | keep the long `read_timeout` on the `connectly-ws` service |

## Production notes

- Put a TLS terminator (ALB/NLB or Kong itself with real certs) on :443; redirect :80 → :443.
- Never publish :8001. On AWS: SG allows 80/443 from the ALB only; 8080/3306/6379 private subnets only.
- Multi-node Kong: DB-less config must ship the same `kong.yml` to every node (bake into the image); use `policy: redis` for shared rate-limit counters.
- CI should validate `kong.yml` (parse + `kong config db_import` against a scratch Kong) before any deploy.

---

## ADR-001: Kafka & gRPC — decision

**Question (from the implementation brief):** does Connectly use Kafka and
gRPC? If not, should we replace internals with them?

**Answer: Kafka — not yet. gRPC — not yet. Both slots are real, neither is
justified by today's workload, and the codebase was deliberately structured
so they can be added without a rewrite.**

Current reality (verified by inspection):

- One Spring Boot monolith. All "services" (auth, posts, chat, presence,
  notifications, search, safety…) are modules in one JVM talking through
  Spring events and direct calls.
- One MySQL database. One client (React SPA) over REST + one STOMP socket.
- No `.proto` files, no gRPC deps, no Kafka deps anywhere.

Why not now:

- **gRPC** pays off with many services making many internal calls. With one
  deployable, gRPC would only add protobuf codegen, a second port, and a
  second auth path — all cost, no benefit. The HTTP/JSON contracts are also
  exactly what the web client consumes.
- **Kafka** pays off with high event volume, fan-out to many consumers, or
  replay needs. Connectly's fan-out (notifications, chat push) is served by
  in-JVM Spring events + WebSocket at current scale; a broker would add a
  stateful cluster to operate before there is load that needs it.

What was done instead (so the future swap is cheap — this is the
"architectural preparedness" part):

- The in-memory `RateLimiter` documents its Redis-backed replacement path for
  multi-node deployments (call sites unchanged).
- `RegistrationCompletedEvent` + `@EventListener` shows the event-carried
  state-transfer pattern; the same record can later be published to a Kafka
  topic by one adapter without touching the publisher.
- JWTs now carry `iss`/`aud`/`jti` and Kong verifies them at the edge — the
  prerequisite for letting independent services trust the same tokens.
- `docker-compose.kong.yml` already runs Redis (the piece most likely to be
  needed first) and gives every future service a place on `connetly-network`.

Adoption triggers (when these become true, implement — in this order):

1. **Second deployable or >1 backend replica** → add gRPC between services
   (protobuf contracts shared in `backend/src/main/proto/`), Redis-backed
   rate limiting, sticky WS routing at the gateway.
2. **Cross-module events need durability/replay or multiple consumers**
   (e.g. moderation queue, analytics, async media processing) → add Kafka;
   start with `spring-kafka` publishing the existing application events to
   topics (`connectly.user.registered`, `connectly.post.created`, …).
3. **Chat presence at real scale** → presence becomes its own service
   (Redis-first), consumed via gRPC; Kong gets a `grpc://` service block.

Until then: no broker, no protobuf, no fake microservices — the gateway +
event seams above are the honest preparation.
