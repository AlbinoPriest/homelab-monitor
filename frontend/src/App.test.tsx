import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { resetCsrfForTests } from './api'
import { App } from './App'
import type { Analytics, Incident, MetricWindow, Monitor, MonitorMetrics } from './types'

vi.mock('./ReliabilityChart', () => ({
  default: () => <div role="img" aria-label="Availability over time" />,
}))

const monitor: Monitor = {
  id: '9d8d235c-949c-4a78-882e-7783cf45845b',
  name: 'NAS dashboard',
  description: 'Storage control plane',
  type: 'HTTP',
  target: 'https://nas.lan/health',
  port: null,
  enabled: true,
  status: 'ONLINE',
  intervalSeconds: 60,
  timeoutMillis: 5000,
  failureThreshold: 3,
  recoveryThreshold: 2,
  latencyWarningMillis: 1000,
  expectedHttpStatus: 200,
  consecutiveFailures: 0,
  consecutiveSuccesses: 4,
  nextCheckAt: '2030-01-01T00:01:00Z',
  lastCheckedAt: '2030-01-01T00:00:00Z',
  createdAt: '2030-01-01T00:00:00Z',
  updatedAt: '2030-01-01T00:00:00Z',
}

function response(body: unknown, status = 200) {
  return Promise.resolve(
    new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
}

const authenticated = {
  setupRequired: false,
  authenticated: true,
  owner: { email: 'owner@example.com', displayName: 'Lab Owner' },
}

function metricsFixture(window: MetricWindow = '24h'): MonitorMetrics {
  return {
    monitorId: monitor.id,
    monitorName: monitor.name,
    window,
    windowStart: '2029-12-31T00:00:00Z',
    windowEnd: '2030-01-01T00:00:00Z',
    dataAvailableFrom: '2029-12-31T00:00:00Z',
    partial: false,
    availableMillis: 80_000,
    unavailableMillis: 20_000,
    excludedMillis: 10_000,
    uptimePercent: 80,
    incidentCount: 1,
    latency: {
      sampleCount: 3,
      averageMillis: 20,
      minMillis: 10,
      maxMillis: 30,
      medianMillis: 20,
      p95Millis: 30,
    },
    buckets: Array.from({ length: 24 }, (_, index) => {
      const start = new Date(Date.UTC(2029, 11, 31, index))
      const end = new Date(start.getTime() + 60 * 60 * 1000)
      return {
        start: start.toISOString(),
        end: end.toISOString(),
        availableMillis: 80,
        unavailableMillis: 20,
        excludedMillis: 0,
        uptimePercent: 80,
      }
    }),
  }
}

const analyticsFixture: Analytics = {
  window: '24h',
  windowStart: '2029-12-31T00:00:00Z',
  windowEnd: '2030-01-01T00:00:00Z',
  overallUptimePercent: 80,
  averageMonitorUptimePercent: 80,
  averageLatencyMillis: 20,
  incidentCount: 1,
  availableMillis: 80_000,
  downtimeMillis: 20_000,
  excludedMillis: 10_000,
  partial: false,
  monitors: [
    {
      monitorId: monitor.id,
      monitorName: monitor.name,
      uptimePercent: 80,
      availableMillis: 80_000,
      downtimeMillis: 20_000,
      excludedMillis: 10_000,
      incidentCount: 1,
      averageLatencyMillis: 20,
      partial: false,
    },
  ],
  slowestMonitors: [],
  leastReliableMonitors: [],
  mostDowntimeMonitors: [],
}

function isAuthStatus(input: RequestInfo | URL) {
  return String(input).endsWith('/api/v1/auth/status')
}

function renderApp(path = '/') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('monitoring dashboard', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
    resetCsrfForTests()
    vi.unstubAllGlobals()
  })

  it('summarizes status and links to monitored services', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (isAuthStatus(input)) return response(authenticated)
      if (String(input).includes('/analytics')) return response(analyticsFixture)
      return response([monitor])
    })
    renderApp()
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect((await screen.findAllByText('NAS dashboard')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('ONLINE').length).toBeGreaterThan(0)
    expect(screen.getByText('Everything looks steady')).toBeInTheDocument()
  })

  it('refreshes authoritative monitor state after a realtime event', async () => {
    class MockEventSource {
      static latest: MockEventSource | undefined
      onopen: (() => void) | null = null
      onmessage: ((event: MessageEvent<string>) => void) | null = null
      onerror: (() => void) | null = null
      close = vi.fn()

      constructor() {
        MockEventSource.latest = this
      }
    }
    vi.stubGlobal('EventSource', MockEventSource)
    let current = monitor
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (isAuthStatus(input)) return response(authenticated)
      if (String(input).includes('/analytics')) return response(analyticsFixture)
      return response([current])
    })
    renderApp()
    expect(await screen.findByText('Everything looks steady')).toBeInTheDocument()

    current = { ...monitor, status: 'OFFLINE' }
    act(() => {
      MockEventSource.latest?.onmessage?.(
        new MessageEvent('message', {
          data: JSON.stringify({ monitorId: monitor.id, changes: ['CHECK_COMPLETED', 'STATUS_CHANGED'] }),
        }),
      )
    })

    await waitFor(() => expect(screen.getAllByText('OFFLINE').length).toBeGreaterThan(0))
  })

  it('resynchronizes on reconnect and cancels queued refreshes during teardown', async () => {
    class MockEventSource {
      static latest: MockEventSource | undefined
      onopen: (() => void) | null = null
      onmessage: ((event: MessageEvent<string>) => void) | null = null
      onerror: (() => void) | null = null
      close = vi.fn()

      constructor() {
        MockEventSource.latest = this
      }
    }
    vi.stubGlobal('EventSource', MockEventSource)
    let current = monitor
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (isAuthStatus(input)) return response(authenticated)
      if (String(input).includes('/analytics')) return response(analyticsFixture)
      return response([current])
    })
    const view = renderApp()
    expect(await screen.findByText('Everything looks steady')).toBeInTheDocument()

    current = { ...monitor, status: 'OFFLINE' }
    act(() => MockEventSource.latest?.onopen?.())
    await waitFor(() => expect(screen.getAllByText('OFFLINE').length).toBeGreaterThan(0))

    act(() => {
      MockEventSource.latest?.onmessage?.(
        new MessageEvent('message', {
          data: JSON.stringify({ monitorId: monitor.id, changes: ['CHECK_COMPLETED'] }),
        }),
      )
    })
    const requestsBeforeTeardown = fetch.mock.calls.length
    view.unmount()
    await new Promise((resolve) => setTimeout(resolve, 150))
    expect(fetch).toHaveBeenCalledTimes(requestsBeforeTeardown)
    expect(MockEventSource.latest?.close).toHaveBeenCalled()
  })

  it('returns to the sign-in gate when the realtime session expires', async () => {
    class MockEventSource {
      static latest: MockEventSource | undefined
      onopen: (() => void) | null = null
      onmessage: ((event: MessageEvent<string>) => void) | null = null
      onerror: (() => void) | null = null
      close = vi.fn()

      constructor() {
        MockEventSource.latest = this
      }
    }
    vi.stubGlobal('EventSource', MockEventSource)
    let authRequests = 0
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (isAuthStatus(input)) {
        authRequests += 1
        return response(
          authRequests === 1
            ? authenticated
            : { ...authenticated, authenticated: false, owner: null },
        )
      }
      if (String(input).includes('/analytics')) return response(analyticsFixture)
      return response([monitor])
    })
    renderApp()
    expect(await screen.findByText('Everything looks steady')).toBeInTheDocument()

    act(() => MockEventSource.latest?.onerror?.())

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(MockEventSource.latest?.close).toHaveBeenCalled()
  })

  it('keeps live monitor state visible when the analytics summary fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (isAuthStatus(input)) return response(authenticated)
      if (String(input).includes('/analytics')) return response({ detail: 'Unavailable' }, 503)
      return response([monitor])
    })

    renderApp()
    expect(await screen.findByText('Everything looks steady')).toBeInTheDocument()
    expect(screen.getAllByText('Unavailable')).toHaveLength(2)
    expect(screen.getByRole('alert')).toHaveTextContent('Reliability summary could not be loaded')
  })

  it('searches and filters the service inventory', async () => {
    const offline = {
      ...monitor,
      id: 'other',
      name: 'Router SSH',
      type: 'TCP' as const,
      target: 'router.lan',
      port: 22,
      status: 'OFFLINE' as const,
    }
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) =>
      isAuthStatus(input) ? response(authenticated) : response([monitor, offline]),
    )
    renderApp('/services')
    const search = await screen.findByRole('textbox', { name: 'Search services' })
    await userEvent.type(search, 'router')
    expect(screen.getByText('Router SSH')).toBeInTheDocument()
    expect(screen.queryByText('NAS dashboard')).not.toBeInTheDocument()
    await userEvent.clear(search)
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Filter status' }), 'ONLINE')
    expect(screen.getByText('NAS dashboard')).toBeInTheDocument()
    expect(screen.queryByText('Router SSH')).not.toBeInTheDocument()
  })

  it('adapts the create form for TCP and sends a CSRF-protected request', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input)
      if (isAuthStatus(input)) return response(authenticated)
      if (url.includes('/metrics')) return response(metricsFixture())
      if (url.endsWith('/csrf')) {
        return response({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'test-token' })
      }
      if (url.endsWith('/monitors') && init?.method === 'POST') {
        return response(
          { ...monitor, name: 'Router SSH', type: 'TCP', target: 'router.lan', port: 22 },
          201,
        )
      }
      return response([])
    })
    renderApp('/services')
    await userEvent.click((await screen.findAllByRole('button', { name: 'Add monitor' }))[0])
    const dialog = screen.getByRole('dialog', { name: 'Add a monitor' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'TCP' }))
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Router SSH')
    await userEvent.type(within(dialog).getByLabelText('Host'), 'router.lan')
    await userEvent.type(within(dialog).getByLabelText('Port'), '22')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create monitor' }))
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/monitors',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'test-token' }),
        }),
      ),
    )
  })

  it('shows detail checks and supports a manual check', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input)
      if (isAuthStatus(input)) return response(authenticated)
      if (url.includes('/metrics')) return response(metricsFixture())
      if (url.endsWith('/csrf')) {
        return response({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'token' })
      }
      if (url.endsWith('/checks') && init?.method === 'POST') {
        return response({
          id: 'check-2',
          result: 'SUCCESS',
          responseTimeMillis: 12,
          checkedAt: '2030-01-01T00:00:10Z',
          errorMessage: null,
          httpStatus: 200,
        })
      }
      if (url.includes('/checks')) {
        return response({
          content: [
            {
              id: 'check-1',
              result: 'SUCCESS',
              responseTimeMillis: 18,
              checkedAt: '2030-01-01T00:00:00Z',
              errorMessage: null,
              httpStatus: 200,
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        })
      }
      if (url.includes('/history'))
        return response({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      if (url.includes('/incidents'))
        return response({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 })
      return response(monitor)
    })
    renderApp(`/services/${monitor.id}`)
    expect(await screen.findByRole('heading', { name: 'NAS dashboard' })).toBeInTheDocument()
    expect(await screen.findByText(/18 ms/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Check now' }))
    expect(await screen.findByText(/check completed: success/i)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/checks'),
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('shows duration-based reliability and switches the selected metrics window', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input)
      if (isAuthStatus(input)) return response(authenticated)
      if (url.includes('/metrics')) {
        const window: MetricWindow = url.includes('window=7d')
          ? '7d'
          : url.includes('window=30d')
            ? '30d'
            : '24h'
        return response(metricsFixture(window))
      }
      if (url.includes('/checks') || url.includes('/history')) {
        return response({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      }
      if (url.includes('/incidents')) {
        return response({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
      }
      return response(monitor)
    })

    renderApp(`/services/${monitor.id}`)
    expect(await screen.findByRole('heading', { name: 'Reliability' })).toBeInTheDocument()
    expect(screen.getAllByText('80.00%')).toHaveLength(3)
    expect(screen.getAllByText('30.0 ms')).toHaveLength(2)
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Time range' }), '7d')
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('window=7d'),
        expect.anything(),
      ),
    )
  })

  it('labels each retention-limited uptime range', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input)
      if (isAuthStatus(input)) return response(authenticated)
      if (url.includes('/metrics')) {
        const metrics = metricsFixture(url.includes('window=30d') ? '30d' : '24h')
        return response(url.includes('window=30d') ? { ...metrics, partial: true } : metrics)
      }
      if (url.includes('/checks') || url.includes('/history')) {
        return response({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      }
      if (url.includes('/incidents')) {
        return response({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
      }
      return response(monitor)
    })

    renderApp(`/services/${monitor.id}`)
    expect(await screen.findByText('Retention-limited')).toBeInTheDocument()
    expect(screen.getByText(/Retention limits this range/)).toBeInTheDocument()
  })

  it('distinguishes failed detail requests from empty history', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input)
      if (isAuthStatus(input)) return response(authenticated)
      if (url.includes('/metrics')) return response(metricsFixture())
      if (url.includes('/checks') || url.includes('/history')) {
        return response({ detail: 'Unavailable' }, 503)
      }
      if (url.includes('/incidents'))
        return response({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 })
      return response(monitor)
    })
    renderApp(`/services/${monitor.id}`)
    expect(await screen.findByText(/checks could not be loaded/i)).toBeInTheDocument()
    expect(await screen.findByText(/state history could not be loaded/i)).toBeInTheDocument()
    expect(screen.queryByText('No checks recorded yet.')).not.toBeInTheDocument()
  })

  it('does not announce empty history while history is loading', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input)
      if (isAuthStatus(input)) return response(authenticated)
      if (url.includes('/metrics')) return response(metricsFixture())
      if (url.includes('/history')) return new Promise<Response>(() => undefined)
      if (url.includes('/checks')) {
        return response({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      }
      if (url.includes('/incidents'))
        return response({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 })
      return response(monitor)
    })
    renderApp(`/services/${monitor.id}`)
    expect(await screen.findByRole('heading', { name: 'State history' })).toBeInTheDocument()
    expect(screen.queryByText('No state transitions recorded.')).not.toBeInTheDocument()
    expect(screen.getAllByLabelText('Loading services').length).toBeGreaterThan(0)
  })

  it('focuses the safe delete action and reports deletion failure', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input)
      if (isAuthStatus(input)) return response(authenticated)
      if (url.includes('/metrics')) return response(metricsFixture())
      if (url.endsWith('/csrf')) {
        return response({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'token' })
      }
      if (init?.method === 'DELETE') return response({ detail: 'Deletion failed safely.' }, 500)
      if (url.includes('/checks') || url.includes('/history')) {
        return response({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      }
      if (url.includes('/incidents'))
        return response({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 })
      return response(monitor)
    })
    renderApp(`/services/${monitor.id}`)
    await userEvent.click(await screen.findByRole('button', { name: 'Delete service' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Delete NAS dashboard?' })
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete monitor' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Deletion failed safely.')
    expect(dialog).toBeInTheDocument()
  })

  it('creates the single owner account with a CSRF-protected request', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input)
      if (isAuthStatus(input)) {
        return response({ setupRequired: true, authenticated: false, owner: null })
      }
      if (url.endsWith('/csrf')) {
        return response({
          headerName: 'X-XSRF-TOKEN',
          parameterName: '_csrf',
          token: 'setup-token',
        })
      }
      if (url.endsWith('/auth/setup') && init?.method === 'POST')
        return response(authenticated, 201)
      if (url.includes('/analytics')) return response(analyticsFixture)
      return response([])
    })
    renderApp()
    expect(await screen.findByRole('heading', { name: 'Secure your monitor' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Display name'), 'Lab Owner')
    await userEvent.type(screen.getByLabelText('Email'), 'owner@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery staple')
    await userEvent.type(screen.getByLabelText('Confirm password'), 'correct horse battery staple')
    await userEvent.click(screen.getByRole('button', { name: 'Create owner account' }))
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/auth/setup',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'setup-token' }),
      }),
    )
  })

  it('lists active incidents with their monitor and supports status filtering', async () => {
    const incident: Incident = {
      id: 'incident-1',
      monitorId: monitor.id,
      status: 'ACTIVE',
      outageReason: 'CONNECTION_REFUSED',
      resolutionReason: null,
      startedAt: '2030-01-01T00:02:00Z',
      endedAt: null,
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input)
      if (isAuthStatus(input)) return response(authenticated)
      if (url.includes('/metrics')) return response(metricsFixture())
      if (url.includes('/incidents')) {
        if (url.includes('status=ACTIVE')) {
          return response({
            content: [incident],
            page: 0,
            size: 1,
            totalElements: 1,
            totalPages: 1,
          })
        }
        const page = url.includes('page=1') ? 1 : 0
        return response({
          content: [incident],
          page,
          size: 20,
          totalElements: 21,
          totalPages: 2,
        })
      }
      return response([monitor])
    })
    renderApp('/incidents')
    expect(await screen.findByRole('heading', { name: 'Incidents' })).toBeInTheDocument()
    expect(await screen.findByText('NAS dashboard')).toBeInTheDocument()
    expect(screen.getByText('connection refused')).toBeInTheDocument()
    expect(screen.getByText('Ongoing')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('page=1'), expect.anything()),
    )
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument()
    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: 'Filter incidents' }),
      'RESOLVED',
    )
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('status=RESOLVED'),
        expect.anything(),
      ),
    )
  })

  it('pages through incident history on a service detail', async () => {
    const incident: Incident = {
      id: 'incident-1',
      monitorId: monitor.id,
      status: 'RESOLVED',
      outageReason: 'TIMEOUT',
      resolutionReason: 'RECOVERED',
      startedAt: '2030-01-01T00:02:00Z',
      endedAt: '2030-01-01T00:03:00Z',
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input)
      if (isAuthStatus(input)) return response(authenticated)
      if (url.includes('/metrics')) return response(metricsFixture())
      if (url.includes('/incidents')) {
        const page = url.includes('page=1') ? 1 : 0
        return response({ content: [incident], page, size: 10, totalElements: 11, totalPages: 2 })
      }
      if (url.includes('/checks') || url.includes('/history')) {
        return response({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      }
      return response(monitor)
    })

    renderApp(`/services/${monitor.id}`)
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('page=1'), expect.anything()),
    )
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument()
  })

  it('returns to the last incident page when a background update empties the current page', async () => {
    const incident: Incident = {
      id: 'incident-1',
      monitorId: monitor.id,
      status: 'RESOLVED',
      outageReason: 'TIMEOUT',
      resolutionReason: 'RECOVERED',
      startedAt: '2030-01-01T00:02:00Z',
      endedAt: '2030-01-01T00:03:00Z',
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input)
      if (isAuthStatus(input)) return response(authenticated)
      if (url.includes('/incidents')) {
        if (url.includes('status=ACTIVE')) {
          return response({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 })
        }
        if (url.includes('page=1')) {
          return response({ content: [], page: 1, size: 20, totalElements: 1, totalPages: 1 })
        }
        return response({
          content: [incident],
          page: 0,
          size: 20,
          totalElements: 21,
          totalPages: 2,
        })
      }
      return response([monitor])
    })

    renderApp('/incidents')
    await userEvent.click(await screen.findByRole('button', { name: 'Next' }))
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('page=1'), expect.anything()),
    )
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    expect(screen.queryByText('Page 2 of 1')).not.toBeInTheDocument()
  })

  it('renders analytics rankings and reloads them for a new time range', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (isAuthStatus(input)) return response(authenticated)
      if (String(input).includes('/analytics')) return response(analyticsFixture)
      return response([])
    })

    renderApp('/analytics')
    expect(await screen.findByRole('heading', { name: 'Analytics' })).toBeInTheDocument()
    expect(await screen.findByText('Overall uptime')).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'All monitor analytics' })).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Time range' }), '30d')
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('window=30d'),
        expect.anything(),
      ),
    )
  })

  it('guides a new owner from empty analytics to service setup', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (isAuthStatus(input)) return response(authenticated)
      if (String(input).includes('/analytics')) {
        return response({
          ...analyticsFixture,
          overallUptimePercent: null,
          averageMonitorUptimePercent: null,
          averageLatencyMillis: null,
          monitors: [],
        })
      }
      return response([])
    })

    renderApp('/analytics')
    expect(await screen.findByRole('heading', { name: 'No analytics yet' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Add a monitor' })).toHaveAttribute('href', '/services')
  })

  it('shows a generic login error without entering the application', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (isAuthStatus(input)) {
        return response({ setupRequired: false, authenticated: false, owner: null })
      }
      if (String(input).endsWith('/csrf')) {
        return response({
          headerName: 'X-XSRF-TOKEN',
          parameterName: '_csrf',
          token: 'login-token',
        })
      }
      return response({ detail: 'Invalid email or password.' }, 401)
    })
    renderApp()
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Email'), 'owner@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
    expect(screen.queryByRole('heading', { name: 'Dashboard' })).not.toBeInTheDocument()
  })

  it('refreshes an expired session CSRF token and retries login once', async () => {
    let csrfRequests = 0
    let loginRequests = 0
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input)
      if (isAuthStatus(input)) {
        return response({ setupRequired: false, authenticated: false, owner: null })
      }
      if (url.endsWith('/csrf')) {
        csrfRequests += 1
        return response({
          headerName: 'X-XSRF-TOKEN',
          parameterName: '_csrf',
          token: csrfRequests === 1 ? 'expired-token' : 'fresh-token',
        })
      }
      if (url.endsWith('/auth/login') && init?.method === 'POST') {
        loginRequests += 1
        return loginRequests === 1 ? response({}, 403) : response(authenticated)
      }
      return response([])
    })
    renderApp()
    await userEvent.type(await screen.findByLabelText('Email'), 'owner@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery staple')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(csrfRequests).toBe(2)
    expect(loginRequests).toBe(2)
  })

  it('returns to the login gate when a protected request reports an expired session', async () => {
    let statusRequests = 0
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (isAuthStatus(input)) {
        statusRequests += 1
        return response(
          statusRequests === 1
            ? authenticated
            : { setupRequired: false, authenticated: false, owner: null },
        )
      }
      return response({ detail: 'Sign in to continue.' }, 401)
    })
    renderApp()
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add monitor' })).not.toBeInTheDocument()
  })
})
