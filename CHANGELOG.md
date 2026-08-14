# Changelog

All notable changes will be documented in this file. The project has not published a working application release yet.

## [Unreleased]

### Added

- Phase 0 repository bootstrap, durable project specification, architecture and deployment skeletons.
- Project-scoped Codex reviewer configuration and contribution templates.
- Java 21 and Spring Boot 4.1 foundation with PostgreSQL, Flyway, Actuator, development OpenAPI, and Testcontainers verification.
- React, TypeScript, Vite, React Router, and TanStack Query foundation with accessible health and failure states.
- Loopback-only PostgreSQL development Compose service, safe example environment values, and GitHub Actions foundation checks.
- Monitor CRUD, paginated check/state history, HTTP and TCP execution, threshold-based status transitions, and non-overlapping scheduled/manual checks.
- Flyway monitoring-core schema with versioned stale-result protection and deterministic restart scheduling.
- Responsive dark-mode dashboard and service-management interface with adaptive HTTP/TCP forms, search, filtering, sorting, manual checks, and state history.
- Singleton first-owner setup, BCrypt password storage, server-side session login/logout, CSRF-protected authentication, authenticated monitor APIs, and frontend route gating.
- Exactly-once outage incidents with recovery/pause resolution, persisted observation-validity windows, deterministic freshness expiry, migration backfill, paginated incident APIs, and responsive incident views.
- Duration-based uptime with explicit excluded time, reachable-check latency statistics, 24-bucket reliability charts, aggregate analytics and rankings across 1h/24h/7d/30d windows, and honest retention-limited responses.
- Configurable bounded-batch raw-check retention with a supporting time index and responsive service/dashboard analytics views.
- Authenticated, bounded, non-replayed Server-Sent Events with session-bound teardown and authoritative client refetch.
- Production Docker images and a health-ordered three-service Compose topology.
- An unprivileged same-origin Nginx proxy with SSE and browser security hardening.
- Production smoke testing plus backup, restore, update, rollback, and secret-lifecycle guidance.
- Genuine dashboard, service-detail, incident, analytics, and mobile screenshots plus an observation-validity ADR.

### Changed

- Completed service inventory filtering with monitor-type and enabled-state controls.
- Added skip navigation, announced loading state, selected-state semantics, balanced dashboard metrics, and a
  responsive two-column mobile filter layout.
- Reworked the README into a concise product, architecture, deployment, verification, security, and scope guide.

### Security

- Private backend/database networking, Secure production sessions, fixed proxy headers, bounded resources,
  non-root containers, read-only application filesystems, an explicit trusted-proxy boundary, source-restricted
  first-owner bootstrap guidance, separate PostgreSQL administrator/non-superuser application roles, and
  fail-closed rejection of unchanged production password placeholders.
