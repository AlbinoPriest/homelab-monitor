# ADR-0003: Use a loopback production proxy

## Status

Accepted

## Context

HomeLab Monitor needs one HTTPS browser origin, static frontend delivery, API/SSE proxying, and private backend
and database services. Bundling automatic public certificate management would couple the application to a DNS
provider, exposure model, and certificate lifecycle that differ across home labs.

## Decision

The production Compose stack serves an unprivileged Nginx frontend/reverse proxy on loopback port 8080 by
default. A host-managed TLS terminator owns certificates and forwards the public HTTPS origin to that listener.
Only the frontend publishes a port. The backend is private on an edge network, PostgreSQL is private on a
separate internal data network, and Nginx disables buffering for the authenticated SSE endpoint.

The host TLS proxy overwrites `X-Forwarded-For`. Nginx accepts a single IP-shaped value from that trusted hop,
falls back to its immediate peer, and overwrites `Host` and client-address forwarding headers for the backend.
The production backend validates and trusts one IP literal from `X-Real-IP` for login throttling only within
this isolated topology. Secure session cookies make HTTPS a production requirement.

## Alternatives considered

- Terminating TLS inside Compose would make first start more self-contained but requires a supported public
  exposure, DNS, challenge, and certificate-storage contract that many private home labs do not share.
- Publishing Spring Boot directly would remove one hop but would lose static serving, single-origin proxying,
  browser header policy, and SSE-specific buffering control at the intended boundary.
- Publishing every service would simplify ad-hoc debugging but needlessly exposes privileged APIs and data.

## Consequences

The deployment composes cleanly with an existing Caddy, Traefik, Nginx, or managed tunnel and does not store
certificate keys in this repository. Operators must provide and maintain HTTPS before first use. Remote TLS
termination requires an explicit private bind address plus a firewall rule. Attaching untrusted containers to
the edge network or publishing the backend would invalidate the forwarding-header trust boundary and is not a
supported topology.
