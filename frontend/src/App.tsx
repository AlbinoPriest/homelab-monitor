import { useQuery } from '@tanstack/react-query'
import { Route, Routes } from 'react-router-dom'

type HealthResponse = {
  status: string
}

async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch('/actuator/health', {
    headers: { Accept: 'application/json' },
  })

  if (!response.ok) {
    throw new Error(`Health request failed with status ${response.status}`)
  }

  return response.json() as Promise<HealthResponse>
}

function FoundationPage() {
  const health = useQuery({
    queryKey: ['backend-health'],
    queryFn: fetchHealth,
    refetchInterval: 30_000,
  })

  const connectionState = health.isPending
    ? 'Checking backend'
    : health.isError
      ? 'Backend unavailable'
      : health.data.status === 'UP'
        ? 'Backend connected'
        : `Backend ${health.data.status.toLowerCase()}`

  return (
    <main className="shell">
      <header className="brand" aria-label="HomeLab Monitor">
        <span className="brand-mark" aria-hidden="true">
          HM
        </span>
        <span>HomeLab Monitor</span>
      </header>

      <section className="foundation-panel" aria-labelledby="foundation-heading">
        <p className="eyebrow">Foundation ready</p>
        <h1 id="foundation-heading">Your home lab, clearly observed.</h1>
        <p className="lede">
          The application foundation is running. HTTP and TCP monitoring arrive in the next build
          phase.
        </p>

        <div className="connection" role="status" aria-live="polite">
          <span
            className={`connection-dot ${health.isSuccess && health.data.status === 'UP' ? 'is-up' : ''}`}
            aria-hidden="true"
          />
          <span>{connectionState}</span>
        </div>

        {health.isError ? (
          <button className="retry-button" type="button" onClick={() => void health.refetch()}>
            Retry connection
          </button>
        ) : null}
      </section>

      <footer>Phase 1 · Application foundation</footer>
    </main>
  )
}

export function App() {
  return (
    <Routes>
      <Route path="*" element={<FoundationPage />} />
    </Routes>
  )
}
