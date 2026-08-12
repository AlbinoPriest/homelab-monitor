# HomeLab Monitor product specification

This document is the durable source of product requirements and delivery status. Future sessions should not require the original bootstrap prompt.

## Delivery status

| Phase | Scope | Status |
| --- | --- | --- |
| 0 | Repository, durable docs, Codex agents, templates | Complete (`1ed1196` on `codex/chore/project-bootstrap`) |
| 1 | Backend/frontend foundation, PostgreSQL, Flyway, development Docker, CI | Complete (`9901b60`–`d1be14e`; backend and DevOps review passed) |
| 2 | Monitor CRUD, checks, executors, scheduler, state engine, history | Complete (`bbfbc81`; verification, smoke test, and review gates passed) |
| 3 | Dashboard and monitor-management UI/API | Complete (`ed43be6`; verification, responsive browser smoke test, and review gates passed) |
| 4 | First-owner setup and session authentication | Not started |
| 5 | Thresholds, incidents, degraded state, pause/freshness reliability | Not started |
| 6 | Duration-based metrics, latency analytics, retention | Not started |
| 7 | Authenticated Server-Sent Events | Not started |
| 8 | Production Docker and security/deployment hardening | Not started |
| 9 | UI, accessibility, README, ADR, changelog, and screenshot polish | Not started |
| 10 | Independent final audit and stable release | Not started |

Update this table at each checkpoint. A phase is not complete until behavior, verification, review, fixes, documentation, and coherent commits are complete.

## Product goal and constraints

HomeLab Monitor is a self-hosted application for monitoring availability, latency, reliability, and incidents for services on a home network. Version 1 supports generic HTTP/HTTPS and TCP monitors. ICMP and vendor integrations are optional roadmap work and must not delay the core.

The target is a single-instance deployment with approximately 100 monitors and one owner account. Prefer a simple, explainable implementation over microservices, queues, caches, distributed locks, Kubernetes, GraphQL, CQRS, or speculative abstractions.

## Technology baseline

Backend:

- Java 21 and the latest stable Spring Boot 4.1.x maintenance release available when Phase 1 begins
- Maven and Maven Wrapper
- Spring MVC, Data JPA, Security, Validation, Scheduling, and Actuator
- PostgreSQL and Flyway with production schema validation
- OpenAPI, JUnit 5, AssertJ, Testcontainers, and Mockito where useful
- Constructor injection, records for suitable DTOs, `Instant`, `Duration`, enums, and no field injection

Frontend:

- React, TypeScript, Vite, React Router, and TanStack Query
- ESLint, Prettier, meaningful frontend tests, and a reputable chart library
- Accessible components, no TypeScript `any`, and no Redux without a demonstrated need

Deployment:

- Docker Compose services for frontend/reverse proxy, backend, and PostgreSQL
- One public browser origin, internal API/SSE proxying, a persistent database volume, health checks, and non-root runtime users where practical
- Windows development and Linux production portability without source changes

## Domain model

Use UUIDs or another documented identifier strategy and appropriate constraints/indexes. Never expose JPA entities directly.

### Owner

One account with email, password hash, display name, and created/updated timestamps. Do not implement multi-user RBAC in version 1.

### Monitor

Fields include name, description, type, target, optional port, enabled flag, current status, interval, timeout, failure threshold, recovery threshold, latency-warning threshold, expected HTTP status where relevant, and timestamps.

### Monitor check

Persist monitor, structured result, response time when meaningful, checked time, structured error type, and a safe error message. Result classes include `SUCCESS`, `TIMEOUT`, `DNS_FAILURE`, `CONNECTION_REFUSED`, `TLS_ERROR`, `UNEXPECTED_STATUS`, `INVALID_TARGET`, and `UNKNOWN_FAILURE`.

### State history

Persist authoritative status transitions with effective timestamps and a reason where useful. This history is the basis for duration metrics; do not duplicate raw checks unnecessarily.

### Incident

Persist monitor, start/end timestamps, outage reason, distinct resolution reason, and active/resolved status. At most one active incident may exist per monitor.

## Monitor execution

Use one small `MonitorExecutor` abstraction with `HttpMonitorExecutor` and `TcpMonitorExecutor` implementations.

HTTP/HTTPS execution must validate syntax, permit only HTTP and HTTPS, enforce timeouts, bound redirects and response reads, avoid storing bodies, classify DNS/TLS/connectivity failures, and never invoke a shell. Private targets are intentionally allowed; the SSRF trust boundary must be documented and protected by owner-only configuration.

TCP execution accepts a hostname or IP, port 1–65535, and bounded timeout, then measures connection latency and returns structured failures.

One monitor failure must never stop the scheduler, application, or unrelated checks.

## Status engine

The backend is authoritative. Statuses are `UNKNOWN`, `ONLINE`, `DEGRADED`, `OFFLINE`, and `PAUSED`.

- A reachable result meeting all expectations is online-quality.
- A reachable result over the latency-warning threshold is degraded-quality.
- Unexpected HTTP status and transport/validation failures are failed results, not degraded results.
- Reachable online/degraded results reset the failure counter.
- A reachable result while `OFFLINE` advances recovery; failure resets recovery.
- Recovery resolves to `ONLINE` or `DEGRADED` according to the latest result.
- New enabled monitors begin `UNKNOWN`. They may become `OFFLINE` after the configured failure threshold.
- Disabled monitors are `PAUSED`, are not scheduled, do not accumulate counters or create incidents, and are excluded from uptime.
- Re-enabling always transitions `PAUSED -> UNKNOWN`; stale state is never restored.
- Freshness expiry may move reachable known state to `UNKNOWN` when observations stop.

Required transitions include all normal moves among online, degraded, and offline; `UNKNOWN` establishment; any active status to paused; paused to unknown; and reachable known status to unknown on freshness expiry.

Test the state machine extensively with interspersed results, thresholds, pausing, re-enabling, freshness, and races.

## Incidents

Create exactly one active incident when the monitor first reaches `OFFLINE` after its failure threshold. Resolve it only after the recovery threshold is reached.

Pausing during an active incident must end it at the pause time with resolution `MONITORING_PAUSED`, then enter `PAUSED`. This is distinct from `RECOVERED`. After re-enable and fresh failures, a new incident may begin.

Incident behavior must remain correct across duplicate checks, concurrent attempts, and application restart.

## Scheduling and concurrency

Each monitor has its own interval. Use bounded scheduler/executor resources rather than one permanent thread per monitor, and prevent overlapping execution for the same monitor.

The Phase 2 design must explicitly resolve scheduled/manual collisions, out-of-order completion, competing transitions, duplicate incidents, disable/delete during active work, interval updates, timeouts, shutdown, and restart with active incidents. Single-instance correctness is sufficient; do not introduce distributed locking.

## History, freshness, and analytics

Support 1 hour, 24 hour, 7 day, and 30 day windows.

Uptime is duration-based:

- `ONLINE` and `DEGRADED`: available and included
- `OFFLINE`: unavailable and included
- `UNKNOWN` and `PAUSED`: excluded
- Calculation begins at monitor creation when it falls inside the requested window

Do not infer uninterrupted availability or downtime when HomeLab Monitor was not observing. Define a deterministic freshness window from interval, timeout, and scheduler tolerance in Phase 5/6, test it, and document its interaction with confirmed offline incidents and restarts. Prefer explicit unknown time over fabricated precision.

Latency statistics include only reachable checks with meaningful latency and provide average, min, max, median, and p95. Never treat failed checks as zero latency.

Raw history retention is configurable (target default: 30 days), cleanup is scheduled, and historical queries are bounded or paginated. Metrics must report partial/insufficient data honestly when retention limits the window.

## Authentication and API security

When no owner exists, `/setup` creates the first owner. Afterward, public setup and arbitrary registration are disabled. Never ship default credentials.

Use Spring Security server-side session authentication with strong password hashing, HttpOnly cookies, sensible SameSite behavior, and CSRF protection. Protect application APIs and frontend routes after setup.

Use versioned `/api/v1` endpoints for auth, monitors, checks, metrics, incidents, dashboard, and analytics. Return consistent safe errors and correct HTTP semantics. Never expose stack traces, SQL details, password material, sessions, secrets, or internal dumps. OpenAPI is development-only.

## Real-time updates

Use authenticated Server-Sent Events for server-to-browser events such as status changes, incidents, and completed checks. Handle disconnect, reconnect, authentication, and repeated state changes without broadcasting unnecessary history.

## Frontend requirements

Create a polished dark-mode-first monitoring interface, not a generic admin starter. Use clear hierarchy, compact useful density, subtle borders/shadows, accessible contrast, and restrained motion. Status must never be color-only.

Primary navigation is Dashboard, Services, Incidents, and Analytics. Do not add a placeholder Settings page.

- Dashboard: service totals, status counts, average uptime/latency, and monitor summaries
- Service detail: status, last check, latency, 24h/7d/30d uptime, time-range chart, checks, incidents, and latency statistics
- Service management: adaptive HTTP/TCP forms, CRUD, enable/disable, manual check, search, filtering, and sorting
- Analytics: overall uptime, incidents, average latency, slowest and least reliable monitors, and downtime by monitor
- Every important view: loading, empty, error, and success states; semantic labels; keyboard access; focus visibility; responsive behavior from mobile through 1920×1080

Use toasts for deliberate user actions, not background checks.

## Persistence and configuration

PostgreSQL runs in Docker for normal use. Flyway owns incremental schema migrations and production uses `ddl-auto=validate`. Add useful time/history indexes.

Never commit `.env` or real secrets. Provide `.env.example` once concrete Phase 1 configuration exists. Use environment variables for database credentials, application secrets, and production configuration. Do not log passwords, tokens, authorization headers, keys, or future secret monitor headers.

Expose only required Actuator health information.

## Test and CI requirements

Prioritize meaningful behavior rather than arbitrary coverage.

- Unit: state machine, thresholds, degraded behavior, pause/re-enable, freshness, incidents, duration uptime, validation
- Integration with Testcontainers PostgreSQL: migrations, repositories, CRUD, auth enforcement, responses, persistence, state history
- Deterministic local HTTP/TCP servers: success, statuses, timeout, refusal, recovery, degradation, pause/re-enable; never depend on a real LAN or public internet
- Frontend: critical flows and important behavior

GitHub Actions runs on push and pull request. Baselines are Maven `verify`, npm clean install/lint/test/build, and practical Docker validation with sensible caching.

## Delivery phases and review gates

1. Foundation — backend and DevOps review.
2. Monitoring core — backend, QA, architecture/concurrency, and SSRF security review.
3. Dashboard/management — frontend and QA review; backend review when needed.
4. Authentication — security, backend, and QA review with adversarial access tests.
5. Incidents/reliability — backend, architecture, and QA red-team review.
6. Metrics/analytics — backend, QA, and frontend review.
7. Realtime — backend, frontend, and QA review.
8. Production/security — security and DevOps review.
9. UI/docs polish — frontend and repository review.
10. Final audit — all relevant reviewers, full verification, smoke tests, deployment inspection, secret/dead-file scan, changelog, and a stable tag only if justified.

Reviewers are read-only. Findings use `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, or `SUGGESTION` severity and include category, files, finding, evidence, impact, and recommendation. The lead classifies each result and fixes accepted important findings with verification.

## Roadmap (not version 1)

Multi-user/RBAC, ICMP, SNMP, Proxmox, Home Assistant, Docker Engine, Tailscale, Wake-on-LAN, certificate/DNS/domain checks, notifications, public status pages, remote agents, and multiple sites remain planned only.

## Next checkpoint

Begin Phase 4 from the verified Phase 3 dashboard/management checkpoint. Implement first-owner setup and session authentication through security, backend, and adversarial QA review. Protect all application API and frontend routes without shipping default credentials or arbitrary registration.
