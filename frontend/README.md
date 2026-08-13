# HomeLab Monitor frontend

React, TypeScript, Vite, React Router, and TanStack Query frontend for HomeLab Monitor.

## Run locally

Use Node.js 24 and npm 11 or newer. Start the backend first, then:

```bash
npm ci
npm run dev
```

Vite proxies `/api` and `/actuator` to the loopback-only backend at `http://127.0.0.1:8080`.

The interface provides a responsive dashboard, searchable and sortable service inventory, adaptive
HTTP/TCP forms, pause/resume and manual-check actions, service details with recent checks and state
history, and authoritative active/resolved incident views.

## Verify

```bash
npm ci
npm run lint
npm test
npm run build
npm run format:check
```
