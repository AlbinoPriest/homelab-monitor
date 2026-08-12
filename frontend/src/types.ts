export type MonitorStatus = 'UNKNOWN' | 'ONLINE' | 'DEGRADED' | 'OFFLINE' | 'PAUSED'
export type MonitorType = 'HTTP' | 'TCP'

export type Monitor = {
  id: string
  name: string
  description: string | null
  type: MonitorType
  target: string
  port: number | null
  enabled: boolean
  status: MonitorStatus
  intervalSeconds: number
  timeoutMillis: number
  failureThreshold: number
  recoveryThreshold: number
  latencyWarningMillis: number | null
  expectedHttpStatus: number | null
  consecutiveFailures: number
  consecutiveSuccesses: number
  nextCheckAt: string | null
  createdAt: string
  updatedAt: string
}

export type MonitorInput = Omit<
  Monitor,
  | 'id'
  | 'status'
  | 'consecutiveFailures'
  | 'consecutiveSuccesses'
  | 'nextCheckAt'
  | 'createdAt'
  | 'updatedAt'
>

export type MonitorCheck = {
  id: string
  result:
    | 'SUCCESS'
    | 'TIMEOUT'
    | 'DNS_FAILURE'
    | 'CONNECTION_REFUSED'
    | 'TLS_ERROR'
    | 'UNEXPECTED_STATUS'
    | 'INVALID_TARGET'
    | 'UNKNOWN_FAILURE'
  responseTimeMillis: number | null
  checkedAt: string
  errorMessage: string | null
  httpStatus: number | null
}

export type StateHistory = {
  id: string
  fromStatus: MonitorStatus | null
  toStatus: MonitorStatus
  effectiveAt: string
  reason: string
}

export type Page<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
