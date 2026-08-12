# Security policy

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Use GitHub's private vulnerability reporting feature for this repository when available. If it is unavailable, contact the repository owner privately through the account listed on the repository and avoid including secrets or exploit details in public channels.

Include the affected version or commit, impact, reproduction steps, and any suggested mitigation. Reports will be acknowledged and triaged as repository maintenance capacity permits; no response-time SLA is promised.

## Supported versions

HomeLab Monitor has not released a supported application version yet. This policy will be updated before the first working release.

## Security model

Version 1 is a single-owner, self-hosted application. The owner can configure HTTP and TCP requests to private network targets. That is intentional and means configuration access is privileged.

The planned application will require strict URL/protocol validation, bounded redirects and response reads, transport timeouts, safe errors, session authentication, CSRF protection, secure cookies, secret-free logs, restricted Actuator exposure, and internal-only database networking. Never expose an unfinished or unauthenticated deployment to untrusted networks.

Do not commit `.env`, credentials, tokens, private keys, or real monitored URLs containing sensitive information.
