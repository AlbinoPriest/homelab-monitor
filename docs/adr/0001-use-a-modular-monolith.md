# ADR-0001: Use a modular monolith

## Status

Accepted

## Context

HomeLab Monitor targets a single home-lab installation with roughly 100 monitors. Monitoring, incidents, authentication, analytics, and real-time updates share transactions and a small operational footprint. The project must be deployable and explainable without unnecessary infrastructure.

## Decision

Build one Spring Boot backend organized by feature boundaries, one React frontend, and one PostgreSQL database. Deploy them with Docker Compose behind a single browser origin. Keep module APIs clear, but add interfaces and layering only when they solve a current testing, dependency, or substitution problem.

## Alternatives considered

- Microservices would isolate features but introduce network failure modes, distributed transactions, additional deployment work, and little value at this scale.
- A package-by-technical-layer monolith would be simple initially but make feature ownership and dependencies less visible as monitoring and incident logic grow.
- A single server-rendered application would reduce runtime pieces but gives up the requested React dashboard experience.

## Consequences

Deployment and local reasoning remain simple, and status/incident changes can use ordinary database transactions. Feature boundaries require discipline rather than network enforcement. The frontend remains a separate build and container, so same-origin proxying and cross-project CI are still necessary.
