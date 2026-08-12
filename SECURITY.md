# Security policy

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Use GitHub's private vulnerability reporting feature for this repository when available. If it is unavailable, contact the repository owner privately through the account listed on the repository and avoid including secrets or exploit details in public channels.

Include the affected version or commit, impact, reproduction steps, and any suggested mitigation. Reports will be acknowledged and triaged as repository maintenance capacity permits; no response-time SLA is promised.

## Supported versions

HomeLab Monitor has not released a supported application version yet. This policy will be updated before the first working release.

## Security model

Version 1 is a single-owner, self-hosted application. The owner can configure HTTP and TCP requests to private network targets. That is intentional and means configuration access is privileged.

The monitoring core enforces URL/protocol validation, bounded and revalidated redirects, closed response streams, end-to-end transport/DNS timeouts, structured safe errors, CSRF protection, restricted Actuator exposure, and loopback-only development backend/database networking. A singleton owner uses BCrypt password hashing and a server-side session; all monitor APIs require authentication. Production network, proxy, and TLS hardening remain Phase 8 work. Enable Secure session cookies whenever the browser origin uses HTTPS, and do not expose an unfinished deployment to untrusted networks.

Do not commit `.env`, credentials, tokens, private keys, or real monitored URLs containing sensitive information.
