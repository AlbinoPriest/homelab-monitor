# Security policy

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Use GitHub's private vulnerability reporting feature for this repository when available. If it is unavailable, contact the repository owner privately through the account listed on the repository and avoid including secrets or exploit details in public channels.

Include the affected version or commit, impact, reproduction steps, and any suggested mitigation. Reports will be acknowledged and triaged as repository maintenance capacity permits; no response-time SLA is promised.

## Supported versions

HomeLab Monitor has not released a supported application version yet. This policy will be updated before the first working release.

## Security model

Version 1 is a single-owner, self-hosted application. The owner can configure HTTP and TCP requests to private network targets. That is intentional and means configuration access is privileged.

The monitoring core enforces URL/protocol validation, bounded and revalidated redirects, closed response
streams, end-to-end transport/DNS timeouts, structured safe errors, CSRF protection, and restricted Actuator
exposure. A singleton owner uses BCrypt password hashing and a server-side session; all monitor APIs require
authentication.

Production publishes only an unprivileged frontend proxy on loopback, keeps the backend and PostgreSQL on
private Compose networks, requires host-managed HTTPS through Secure session cookies, overwrites forwarding
headers, disables SSE buffering, and applies browser security headers. Login throttling trusts `X-Real-IP` only
inside that isolated proxy/backend network. The backend connects to PostgreSQL as a dedicated non-superuser
role, separate from the initialization/backup administrator. Claim the singleton owner through source-restricted
HTTPS before allowing untrusted access to a fresh deployment. Do not publish backend or database ports, attach
untrusted containers to the edge network, bypass HTTPS, or expose the loopback listener on an untrusted interface.

Do not commit `.env`, credentials, tokens, private keys, or real monitored URLs containing sensitive information.
