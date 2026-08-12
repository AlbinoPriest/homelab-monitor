import type { Monitor, MonitorCheck, MonitorInput, Page, StateHistory } from './types'

type CsrfToken = { headerName: string; parameterName: string; token: string }
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
  if (!response.ok) throw await parseError(response)
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

async function mutation<T>(path: string, method: string, body?: unknown): Promise<T> {
  csrfToken ??= request<CsrfToken>('/api/v1/csrf').catch((error: unknown) => {
    csrfToken = undefined
    throw error
  })
  const csrf = await csrfToken
  return request<T>(path, {
    method,
    headers: {
      [csrf.headerName]: csrf.token,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export const api = {
  listMonitors: () => request<Monitor[]>('/api/v1/monitors'),
  getMonitor: (id: string) => request<Monitor>(`/api/v1/monitors/${id}`),
  getChecks: (id: string) => request<Page<MonitorCheck>>(`/api/v1/monitors/${id}/checks?size=20`),
  getHistory: (id: string) => request<Page<StateHistory>>(`/api/v1/monitors/${id}/history?size=20`),
  createMonitor: (input: MonitorInput) => mutation<Monitor>('/api/v1/monitors', 'POST', input),
  updateMonitor: (id: string, input: MonitorInput) =>
    mutation<Monitor>(`/api/v1/monitors/${id}`, 'PUT', input),
  deleteMonitor: (id: string) => mutation<void>(`/api/v1/monitors/${id}`, 'DELETE'),
  checkMonitor: (id: string) => mutation<MonitorCheck>(`/api/v1/monitors/${id}/checks`, 'POST'),
}

export function resetCsrfForTests() {
  csrfToken = undefined
}
