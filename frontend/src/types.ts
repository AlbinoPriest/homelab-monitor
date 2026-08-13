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
  lastCheckedAt: string | null
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
  | 'lastCheckedAt'
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

export type IncidentStatus = 'ACTIVE' | 'RESOLVED'
export type Incident = {
  id: string
  monitorId: string
  status: IncidentStatus
  outageReason: Exclude<MonitorCheck['result'], 'SUCCESS'>
  resolutionReason: 'RECOVERED' | 'MONITORING_PAUSED' | null
  startedAt: string
  endedAt: string | null
}

export type Page<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
