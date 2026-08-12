# HomeLab Monitor backend

Java 21 and Spring Boot 4.1 backend for HomeLab Monitor.

## Run locally

Start PostgreSQL from the repository root, then run the application with the development profile.

Windows PowerShell:

```powershell
docker compose -f docker-compose.dev.yml up -d
cd backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

POSIX:

```bash
docker compose -f docker-compose.dev.yml up -d
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The health endpoint is `http://localhost:8080/actuator/health`. With the `dev` profile, OpenAPI JSON is available at `http://localhost:8080/v3/api-docs`.
The development profile also imports the repository-root `.env`, keeping its database settings aligned with Docker Compose.

## Verify

```powershell
.\mvnw.cmd verify
```

Integration tests use Testcontainers and require a running Docker engine.
