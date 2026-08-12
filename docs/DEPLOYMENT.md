# Deployment

This is the deployment contract. Phase 1 provides a verified local development workflow; production images and deployment remain Phase 8 work.

## Target topology — planned

```text
Browser -> frontend/reverse proxy -> backend -> PostgreSQL
                                  -> monitored HTTP/TCP targets
```

Production exposes one browser origin. The backend and PostgreSQL use internal Compose networking, and PostgreSQL uses a named volume.

## Development on Windows

Prerequisites are Java 21, Node.js 24 or later with npm 11 or later, Docker Engine with Docker Compose, and Git. PostgreSQL runs in Docker while the backend and frontend run directly. The database port binds to loopback only.

```powershell
Copy-Item .env.example .env
docker compose -f docker-compose.dev.yml up -d

cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

cd ..\frontend
npm ci
npm run dev
```

The frontend is served at `http://localhost:5173` and proxies `/actuator` and future `/api` calls to the backend at `http://localhost:8080`. Development OpenAPI JSON is available at `http://localhost:8080/v3/api-docs`.
The authenticated development backend binds to `127.0.0.1` and validates loopback Host headers by default. On first launch, open the frontend and create the singleton owner account. Set `SERVER_ADDRESS` and a matching comma-separated `ALLOWED_HOSTS` value only when intentionally exposing it on a trusted network.

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

Stop PostgreSQL with `docker compose -f docker-compose.dev.yml down`. The named volume is retained; add `--volumes` only when intentionally discarding local development data.

## Linux production deployment — planned for Phase 8

```bash
git clone <repository-url>
cd homelab-monitor
cp .env.example .env
# Edit .env with unique production credentials and secrets.
docker compose up -d --build
docker compose ps
```

Phase 8 must document TLS/reverse-proxy assumptions, ports, health checks, restart policies, resource expectations, and a smoke test. PostgreSQL must not be publicly exposed by default.

## Updating — planned

The final process will include backing up, fetching the intended release, rebuilding images, allowing Flyway to migrate on backend startup, verifying health, and retaining a rollback path. Do not downgrade across Flyway migrations without an explicit compatible procedure.

## Development environment configuration

Copy `.env.example` to `.env` to override these local defaults. `.env` is ignored. The checked-in password is intentionally development-only; never reuse it for production.

| Variable | Default | Purpose |
| --- | --- | --- |
| `POSTGRES_DB` | `homelab_monitor` | Development database name |
| `POSTGRES_USER` | `homelab_monitor` | Development database user |
| `POSTGRES_PASSWORD` | `homelab_monitor_dev` | Development-only database password |
| `POSTGRES_PORT` | `5432` | Loopback host port for PostgreSQL |
| `SESSION_TIMEOUT` | `30m` | Inactivity timeout for the owner session |
| `SESSION_COOKIE_SECURE` | `false` | Set to `true` when the browser origin uses HTTPS |
The backend development profile imports the repository-root `.env`, so the same `POSTGRES_*` values configure Compose and the locally launched application. Environment variables still take precedence over file values. Phase 8 will document every production variable and secret lifecycle.

## PostgreSQL backup — planned for Phase 8

Use standard PostgreSQL custom-format backups through the Compose service. The final command will follow this shape after the service/database names are fixed:

```bash
docker compose exec -T postgres pg_dump --format=custom --username=<user> --dbname=<database> > homelab-monitor.dump
```

PowerShell binary redirection can corrupt native-command byte streams on older Windows PowerShell. The verified Windows backup procedure must either use PowerShell 7 with byte-safe redirection or write the dump inside the container and copy it out.

## PostgreSQL restore — planned for Phase 8

Restoration will use `pg_restore` into a clean, compatible database after stopping application writes. The final guide must provide tested POSIX and PowerShell-safe commands, version compatibility notes, and post-restore health checks.

## Migration guarantees

Flyway migrations are forward-only, incremental, reviewed with schema changes, and run before JPA validation. A database backup is required before production upgrades that include migrations. Never use Hibernate schema generation as the production migration mechanism.
