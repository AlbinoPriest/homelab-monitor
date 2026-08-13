import type {
  Incident,
  IncidentStatus,
  Monitor,
  MonitorCheck,
  MonitorInput,
  Page,
  StateHistory,
} from './types'

type CsrfToken = { headerName: string; parameterName: string; token: string }
export type OwnerSummary = { email: string; displayName: string }
export type AuthStatus = {
  setupRequired: boolean
  authenticated: boolean
  owner: OwnerSummary | null
}
export type SetupInput = OwnerSummary & { password: string }
export type LoginInput = { email: string; password: string }
let csrfToken: Promise<CsrfToken> | undefined

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
  }
}

async function parseError(response: Response): Promise<ApiError> {
  const fallback = `Request failed with status ${response.status}`
  try {
    const body = (await response.json()) as { detail?: string; title?: string }
    return new ApiError(body.detail ?? body.title ?? fallback, response.status)
  } catch {
    return new ApiError(fallback, response.status)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { Accept: 'application/json', ...init?.headers },
  })
  if (!response.ok) {
    if (response.status === 401 && path !== '/api/v1/auth/status') {
      window.dispatchEvent(new Event('homelab-auth-required'))
    }
    throw await parseError(response)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

async function mutation<T>(path: string, method: string, body?: unknown): Promise<T> {
  async function send(retried: boolean): Promise<T> {
    csrfToken ??= request<CsrfToken>('/api/v1/csrf').catch((error: unknown) => {
      csrfToken = undefined
      throw error
    })
    const csrf = await csrfToken
    try {
      return await request<T>(path, {
        method,
        headers: {
          [csrf.headerName]: csrf.token,
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      })
    } catch (error) {
      if (!retried && error instanceof ApiError && error.status === 403) {
        csrfToken = undefined
        return send(true)
      }
      throw error
    }
  }
  return send(false)
}

export const api = {
  authStatus: () => request<AuthStatus>('/api/v1/auth/status'),
  setupOwner: (input: SetupInput) => mutation<AuthStatus>('/api/v1/auth/setup', 'POST', input),
  login: (input: LoginInput) => mutation<AuthStatus>('/api/v1/auth/login', 'POST', input),
  logout: async () => {
    try {
      return await mutation<void>('/api/v1/auth/logout', 'POST')
    } finally {
      csrfToken = undefined
    }
  },
  listMonitors: () => request<Monitor[]>('/api/v1/monitors'),
  getMonitor: (id: string) => request<Monitor>(`/api/v1/monitors/${id}`),
  getChecks: (id: string) => request<Page<MonitorCheck>>(`/api/v1/monitors/${id}/checks?size=20`),
  getHistory: (id: string) => request<Page<StateHistory>>(`/api/v1/monitors/${id}/history?size=20`),
  getIncidents: (options?: {
    monitorId?: string
    status?: IncidentStatus
    page?: number
    size?: number
  }) => {
    const parameters = new URLSearchParams({
      page: String(options?.page ?? 0),
      size: String(options?.size ?? 20),
    })
    if (options?.monitorId) parameters.set('monitorId', options.monitorId)
    if (options?.status) parameters.set('status', options.status)
    return request<Page<Incident>>(`/api/v1/incidents?${parameters}`)
  },
  createMonitor: (input: MonitorInput) => mutation<Monitor>('/api/v1/monitors', 'POST', input),
  updateMonitor: (id: string, input: MonitorInput) =>
    mutation<Monitor>(`/api/v1/monitors/${id}`, 'PUT', input),
  deleteMonitor: (id: string) => mutation<void>(`/api/v1/monitors/${id}`, 'DELETE'),
  checkMonitor: (id: string) => mutation<MonitorCheck>(`/api/v1/monitors/${id}/checks`, 'POST'),
}

export function resetCsrfForTests() {
  csrfToken = undefined
}
