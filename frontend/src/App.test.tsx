import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { resetCsrfForTests } from './api'
import { App } from './App'
import type { Monitor } from './types'

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
  })

  it('summarizes status and links to monitored services', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response([monitor]))
    renderApp()
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect((await screen.findAllByText('NAS dashboard')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('ONLINE').length).toBeGreaterThan(0)
    expect(screen.getByText('Everything looks steady')).toBeInTheDocument()
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
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response([monitor, offline]))
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

  it('distinguishes failed detail requests from empty history', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input)
      if (url.includes('/checks') || url.includes('/history')) {
        return response({ detail: 'Unavailable' }, 503)
      }
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
      if (url.includes('/history')) return new Promise<Response>(() => undefined)
      if (url.includes('/checks')) {
        return response({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      }
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
      if (url.endsWith('/csrf')) {
        return response({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'token' })
      }
      if (init?.method === 'DELETE') return response({ detail: 'Deletion failed safely.' }, 500)
      if (url.includes('/checks') || url.includes('/history')) {
        return response({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      }
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
})
