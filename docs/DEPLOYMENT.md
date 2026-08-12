# Deployment

This is the deployment contract and Phase 0 skeleton. Docker files, environment variables, images, and runnable commands will be implemented in later phases; commands marked **planned** must not be treated as working yet.

## Target topology — planned

```text
Browser -> frontend/reverse proxy -> backend -> PostgreSQL
                                  -> monitored HTTP/TCP targets
```

Production exposes one browser origin. The backend and PostgreSQL use internal Compose networking, and PostgreSQL uses a named volume.

## Development on Windows — planned for Phase 1

Prerequisites will include Java 21, Node.js, Docker Desktop, and Git. PostgreSQL will run in Docker while the backend and frontend may run directly.

```powershell
Copy-Item .env.example .env
docker compose -f docker-compose.dev.yml up -d

cd backend
.\mvnw.cmd spring-boot:run

cd ..\frontend
npm install
npm run dev
```

Exact Node and Docker minimum versions and the concrete environment variables will be documented after the scaffolds exist.

## Development on POSIX — planned for Phase 1

```bash
cp .env.example .env
docker compose -f docker-compose.dev.yml up -d

cd backend
./mvnw spring-boot:run

cd ../frontend
npm install
npm run dev
```

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

## Environment configuration — pending Phase 1/8

`.env.example` will contain names and safe placeholders only. `.env` is ignored. No real database password or application secret belongs in Git. The final table must describe every variable, default, required status, and whether changing it affects stored sessions or data.

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
