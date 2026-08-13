# HomeLab Monitor

HomeLab Monitor is a self-hosted application for tracking the availability, latency, reliability, and incidents of HTTP/HTTPS and TCP services on a home network.

> [!IMPORTANT]
> The repository contains the monitoring core, management interface, owner authentication, incident/reliability behavior, and Phase 6 metrics/analytics implementation. Realtime updates and production deployment remain planned.

## Version 1 scope

- Generic HTTP/HTTPS monitoring with expected-status and latency checks
- Generic TCP connectivity and connection-latency monitoring
- A centralized `UNKNOWN`, `ONLINE`, `DEGRADED`, `OFFLINE`, and `PAUSED` state model
- Failure and recovery thresholds with incident tracking
- Duration-based uptime and bounded observation-freshness semantics
- Single-owner setup with session authentication
- A dark-mode-first React dashboard with service, incident, and analytics views
- Docker Compose deployment for the frontend, backend, and PostgreSQL

Vendor-specific integrations, notifications, public status pages, and multi-user access are roadmap items rather than version 1 requirements.

## Architecture

HomeLab Monitor will be a modular monolith: a Spring Boot backend, React frontend, and PostgreSQL database deployed as three Docker Compose services. This keeps deployment simple while preserving clear feature boundaries.

```mermaid
flowchart LR
    Browser["Browser"] --> Frontend["Frontend / reverse proxy"]
    Frontend --> Backend["Spring Boot API"]
    Backend --> Database["PostgreSQL"]
    Backend --> Targets["HTTP and TCP targets"]
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for design constraints and decisions.

## Repository layout

```text
backend/           Spring Boot application
frontend/          React application
docs/              Product, architecture, deployment, and ADR documentation
.codex/            Project-scoped Codex and reviewer configuration
.github/           Contribution templates
```

## Development status

Phase 6 adds duration-based uptime, reachable-check latency statistics, retention-aware analytics, service reliability charts, and bounded raw-check cleanup to the authenticated monitoring application.

For a local development run, follow [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md). The authoritative requirements and phase status live in [docs/PROJECT_SPEC.md](docs/PROJECT_SPEC.md).

## Security model

HomeLab Monitor is intentionally allowed to contact private network targets. That capability creates an SSRF risk if untrusted people can configure monitors, so version 1 uses a single authenticated owner, strict protocol and target validation, bounded redirects and response reads, and safe error reporting. See [SECURITY.md](SECURITY.md).

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md) before making changes. Documentation must describe working behavior truthfully; planned behavior must remain clearly labeled.

## License

Licensed under the [MIT License](LICENSE).
