# HomeLab Monitor agent guide

Read `docs/PROJECT_SPEC.md` before implementation and the relevant sections of `docs/ARCHITECTURE.md` before changing behavior.

## Engineering rules

- Prioritize correctness, security, readability, maintainability, tests, UX, deployment reliability, performance, then extensibility.
- Build a comprehensible modular monolith. Do not add infrastructure, patterns, modules, or abstractions without a current requirement.
- Use Java 21, a stable Spring Boot 4.1.x maintenance release, Maven, React, TypeScript, Vite, PostgreSQL, Flyway, and Docker Compose as specified.
- Never use field injection, expose JPA entities from controllers, commit secrets, log credentials, or knowingly commit broken code.
- Treat backend state as authoritative. Preserve the documented status, incident, freshness, and duration-based uptime semantics.
- Keep documentation truthful and synchronized with implementation. Label planned behavior explicitly.
- Preserve unrelated user changes and avoid destructive Git operations.

## Verification

After Phase 1 creates the projects, run the checks relevant to the change.

Windows PowerShell:

```powershell
cd backend
.\mvnw.cmd verify

cd ..\frontend
npm ci
npm run lint
npm test
npm run build

cd ..
docker compose -f docker-compose.dev.yml config --quiet
```

POSIX:

```bash
cd backend
./mvnw verify

cd ../frontend
npm ci
npm run lint
npm test
npm run build

cd ..
docker compose -f docker-compose.dev.yml config --quiet
```

Never report a check as passing when it did not run.

## Git and phase discipline

- Use focused branches and coherent Conventional Commit-style commits. Inspect the diff and staged files before committing.
- A phase is done only when its behavior works, relevant checks pass, required review ran, accepted important findings are fixed, documentation is current, and the branch is merge-ready.
- Use the smallest relevant read-only review team from `.codex/agents/`. Reviewers must report evidence and must not edit files.
- Resolve all critical findings. Fix high findings or document a strong reason to defer them.
- At every phase checkpoint, update the phase status and durable decisions before deciding whether to continue.
- Continue in the current session only while architecture, test state, findings, and next work remain clear. Stop with a clean handoff when configuration reload, context quality, a major decision, or an external blocker makes a fresh session safer.

## Current checkpoint

Phase 2 establishes monitor CRUD, bounded HTTP/TCP checks, scheduling, authoritative state transitions, and paginated history. After its verified commit, Phase 3 is next: build the dashboard and monitor-management experience without pulling Phase 4 authentication work forward.
