# Deployment

This document is the deployment and recovery contract for HomeLab Monitor. Development runs PostgreSQL in
Docker while the application projects run directly. Production runs the frontend proxy, backend, and
PostgreSQL as one Docker Compose project.

## Production topology

```text
Browser --HTTPS--> host TLS proxy --> 127.0.0.1:8080 frontend/Nginx
                                             |-- /api, /actuator/health --> backend:8080
                                             |-- static React assets
                                                        |
                                                        +--> PostgreSQL:5432
                                                        +--> monitored HTTP/TCP targets
```

Only the unprivileged frontend proxy publishes a host port. The backend is reachable only on the Compose
edge network, and PostgreSQL is reachable only on an internal data network. The backend also uses the edge
network for outbound checks to home-lab targets. PostgreSQL data lives in the `postgres-data` named volume.

The checked-in proxy serves plain HTTP on loopback because certificate ownership and renewal belong to the
host's existing TLS terminator (for example Caddy, Traefik, or Nginx). Production session cookies are always
`Secure`; do not expose the loopback HTTP listener directly to browsers. A minimal host Caddy configuration is:

```caddyfile
monitor.example.net {
    reverse_proxy 127.0.0.1:8080
}
```

If the TLS proxy runs on another trusted machine, set `APP_BIND_ADDRESS` to the host's private address and
restrict that port with a firewall to the proxy source. Never publish the backend or database ports.

## Production prerequisites

- A Linux host with Docker Engine and Docker Compose v2
- A DNS name and HTTPS termination trusted by the browsers that will use the application
- Approximately 1.5 GiB RAM and 3 CPU cores within the checked-in limits, plus storage for PostgreSQL,
  container images, and backups
- Egress from the Docker host to every HTTP/TCP target the owner intends to monitor

## First production start

The setup endpoint is intentionally available until the singleton owner exists. **Do not expose a fresh
instance to an untrusted network before claiming the owner account.** Configure the HTTPS terminator first,
but temporarily restrict its inbound port to the operator's source IP (or use an equivalent private VPN/firewall
path). Keep that restriction in place through the commands and owner setup below. Plain loopback HTTP is not a
substitute because production session cookies require HTTPS.

```bash
git clone <repository-url>
cd homelab-monitor
cp .env.production.example .env
openssl rand -base64 36
# Generate a second value. Put the two different values in POSTGRES_ADMIN_PASSWORD
# and APP_DB_PASSWORD, then review every .env setting.
chmod 600 .env

docker compose config --quiet
docker compose up -d --build
docker compose ps
sh scripts/smoke-production.sh https://monitor.example.net
```

All three services must report `healthy`. While HTTPS remains source-restricted, open the origin and create the
singleton owner. Confirm that signing out and back in works, then remove only the temporary source restriction
needed for bootstrap. There are no default application credentials. If an unexpected owner already exists,
stop the deployment and investigate instead of using it. Keep `.env` readable only by the deployment account
and include neither it nor database dumps in source control.

First initialization fails closed if either the role names or passwords are identical. If this happens on a genuinely
new deployment with no data, correct `.env`, confirm that the newly created `postgres-data` volume contains
nothing worth retaining, then run `docker compose down --volumes` before starting again. Never remove that
volume to repair an established deployment; rotate the roles interactively as described below instead.

## Production configuration

Compose reads `.env` from the repository root. The two database role names and their required passwords must
be different and each password must contain at least 20 characters; neither password has a production default. PostgreSQL's initialization role is reserved for
administration; the backend and Flyway connect as the non-superuser application role created during first
database initialization.

| Variable | Default | Purpose |
| --- | --- | --- |
| `POSTGRES_DB` | `homelab_monitor` | Database name |
| `POSTGRES_ADMIN_USER` | `homelab_admin` | PostgreSQL initialization/backup role; never supplied to the backend |
| `POSTGRES_ADMIN_PASSWORD` | required | Random password of at least 20 characters for the PostgreSQL administration role |
| `APP_DB_USER` | `homelab_app` | Non-superuser role used by the backend and Flyway |
| `APP_DB_PASSWORD` | required | Different random password of at least 20 characters for the application role |
| `APP_BIND_ADDRESS` | `127.0.0.1` | Address on which the frontend proxy publishes |
| `APP_PORT` | `8080` | Host port used by the TLS terminator |
| `SESSION_TIMEOUT` | `30m` | Owner-session inactivity timeout |
| `RAW_CHECK_RETENTION_ENABLED` | `true` | Enables bounded raw-check cleanup |
| `RAW_CHECK_RETENTION_DAYS` | `30` | Retained raw-check history |
| `RAW_CHECK_RETENTION_BATCH_SIZE` | `1000` | Rows removed per cleanup batch |
| `RAW_CHECK_RETENTION_MAX_BATCHES_PER_RUN` | `2` | Maximum cleanup batches per scheduled run |
| `RAW_CHECK_RETENTION_INITIAL_DELAY` | `60000` | Cleanup startup delay in milliseconds |
| `RAW_CHECK_RETENTION_CLEANUP_DELAY` | `60000` | Delay between cleanup runs in milliseconds |
| `SSE_MAX_CONNECTIONS` | `8` | Concurrent authenticated event streams |
| `SSE_CONNECTION_TIMEOUT` | `300000` | Event-stream lifetime in milliseconds |
| `SSE_HEARTBEAT_DELAY` | `15000` | Event-stream heartbeat interval in milliseconds |

`SPRING_PROFILES_ACTIVE`, database host, backend bind address, Secure cookies, internal Host allowlist, and
trusted proxy handling are fixed by the production Compose/profile contract rather than user-facing knobs.
The host TLS terminator must overwrite `X-Forwarded-For` with the real client address; Caddy does this by
default and ignores spoofed incoming values. The frontend accepts only a single IP-shaped value from that
trusted hop, otherwise falls back to the immediate peer, and overwrites the headers sent to the backend. The
backend then validates and trusts the single `X-Real-IP` literal only in the isolated production topology. Do
not attach untrusted containers to the edge network or expose the backend port. If another proxy sits in front
of the TLS terminator, configure that terminator's trusted-proxy ranges explicitly rather than trusting arbitrary
forwarded chains.

Changing either password in `.env` does not rotate an existing PostgreSQL role. During a maintenance window,
connect interactively with
`docker compose exec postgres sh -c 'psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'`, use
`\password homelab_admin` or
`\password homelab_app` (substitute custom role names) so the new secret is not placed in shell history,
update the matching `.env` value, and recreate the affected services. Never grant the application role
superuser, role-creation, or database-creation privileges.

## Health, resources, and shutdown

- Frontend health: proxied `/actuator/health`, verifying the complete frontend-to-backend path.
- Backend health: `/actuator/health`, with details hidden and no other Actuator endpoints exposed.
- PostgreSQL health: `pg_isready` against the configured database and role.
- Startup is ordered by health: PostgreSQL, backend/Flyway, then frontend.
- Restart policy is `unless-stopped` for all services.
- Memory limits are 128 MiB frontend, 768 MiB backend, and 512 MiB PostgreSQL. CPU and PID limits are also
  set; tune only after observing the host, and preserve bounded application worker pools.
- Frontend and backend run as non-root with read-only root filesystems, dropped Linux capabilities,
  `no-new-privileges`, and small tmpfs mounts. PostgreSQL retains only its named data volume.
- Backend receives 80 seconds and PostgreSQL 60 seconds for graceful stop; the backend's Spring lifecycle budget
  is 70 seconds so its supported two-wave scheduled-check envelope can finish first.

Useful inspection commands:

```bash
docker compose ps
docker compose logs --tail=200 frontend backend postgres
docker compose exec backend id
docker compose exec frontend id
```

## Smoke test

Run the read-only smoke script after first deployment and every update:

```bash
sh scripts/smoke-production.sh https://monitor.example.net
```

It verifies the React entry point, security headers, proxied health, public auth status, protected API `401`,
and Secure/HttpOnly/SameSite session cookie attributes. It does not create an owner or modify monitoring data.

## Backup

Create a custom-format dump inside the container, copy it to the host, then remove the temporary container file.
This works from POSIX shells and Windows PowerShell without native binary-redirection corruption:

```bash
docker compose exec -T postgres sh -c \
  'pg_dump --format=custom --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file=/tmp/homelab-monitor.dump'
docker compose cp postgres:/tmp/homelab-monitor.dump ./homelab-monitor-$(date +%Y%m%d-%H%M%S).dump
docker compose exec -T postgres rm -f /tmp/homelab-monitor.dump
```

PowerShell uses the same container-side dump and copy pattern:

```powershell
$backup = "homelab-monitor-$(Get-Date -Format yyyyMMdd-HHmmss).dump"
docker compose exec -T postgres sh -c 'pg_dump --format=custom --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file=/tmp/homelab-monitor.dump'
docker compose cp postgres:/tmp/homelab-monitor.dump ".\$backup"
docker compose exec -T postgres rm -f /tmp/homelab-monitor.dump
```

Protect backups as credentials: restrict permissions, store copies off-host, and test restoration periodically.
Use `pg_restore` from the same PostgreSQL major version or a newer compatible client.

## Restore

Restoration replaces the current application database. Take a fresh backup first and confirm the exact dump
path. Stop application writes, copy the dump into PostgreSQL, recreate the database, reapply the restricted
application-role grants, restore objects as that role without dump ownership or privilege statements, then
restart and smoke test:

```bash
docker compose stop frontend backend
docker compose cp ./homelab-monitor.dump postgres:/tmp/restore.dump
docker compose exec -T postgres sh -c \
  'dropdb --force --if-exists --username="$POSTGRES_USER" "$POSTGRES_DB" && createdb --username="$POSTGRES_USER" "$POSTGRES_DB"'
docker compose exec -T postgres sh /docker-entrypoint-initdb.d/10-create-app-role.sh
docker compose exec -T postgres sh -c \
  'pg_restore --exit-on-error --no-owner --no-privileges --role="$APP_DB_USER" --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" /tmp/restore.dump'
docker compose exec -T postgres rm -f /tmp/restore.dump
docker compose up -d backend frontend
docker compose ps
sh scripts/smoke-production.sh https://monitor.example.net
```

The same commands are PowerShell-safe because the dump bytes move through `docker compose cp`, not shell
redirection. Restore only backups from a schema/application version you understand; Flyway will apply any
newer forward migrations when the backend starts.

## Updating and rollback

1. Record the deployed Git commit and create a verified backup.
2. Fetch the intended tag or commit and inspect release notes and migrations.
3. Run `docker compose config --quiet` and `docker compose build`.
4. Run `docker compose up -d`; the backend applies forward-only Flyway migrations before becoming healthy.
5. Run `docker compose ps` and the production smoke test, then inspect logs.

For an application-only failure with no new migration, check out the prior commit and rebuild. If a migration
ran, do not start older code against the newer schema unless compatibility is explicitly documented; restore
the pre-upgrade backup together with the prior code instead. Never edit Flyway history or downgrade a schema
in place.

### Updating pinned base images

Production Dockerfiles and PostgreSQL retain readable version tags but also pin reviewed multi-architecture
SHA-256 digests, so rebuilding one commit is deterministic. At least monthly and after relevant security
advisories, inspect the official Temurin, Node, Nginx-unprivileged, and PostgreSQL release sources; select an
explicit maintenance tag, resolve its current manifest-list digest with `docker buildx imagetools inspect`, and
update tag and digest together. Rebuild without cache, run the full test suite and isolated production smoke
test, inspect image users and exposed ports, and submit the digest change through the normal security/DevOps
review. Never change only a digest while leaving a misleading tag.

## Development on Windows

Prerequisites are Java 21, Node.js 24 or later with npm 11 or later, Docker Engine with Docker Compose, and Git.

```powershell
Copy-Item .env.example .env
docker compose -f docker-compose.dev.yml up -d

cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

cd ..\frontend
npm ci
npm run dev
```

The frontend is at `http://localhost:5173`; it proxies `/api` and `/actuator` to the loopback backend. The
development OpenAPI JSON is at `http://localhost:8080/v3/api-docs`. Create the owner on first launch. Set
`SERVER_ADDRESS` and matching comma-separated `ALLOWED_HOSTS` only for deliberate trusted-LAN development.

## Development on POSIX

```bash
cp .env.example .env
docker compose -f docker-compose.dev.yml up -d

cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

cd ../frontend
npm ci
npm run dev
```

Stop development PostgreSQL with `docker compose -f docker-compose.dev.yml down`. The named volume is retained;
add `--volumes` only when intentionally discarding local development data.
