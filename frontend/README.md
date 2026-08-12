# HomeLab Monitor frontend

React, TypeScript, Vite, React Router, and TanStack Query frontend for HomeLab Monitor.

## Run locally

Use Node.js 24 and npm 11 or newer. Start the backend first, then:

```bash
npm ci
npm run dev
```

Vite proxies `/api` and `/actuator` to `http://localhost:8080`.

## Verify

```bash
npm ci
npm run lint
npm test
npm run build
npm run format:check
```
