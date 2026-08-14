import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, NavLink, Route, Routes, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError, type AuthStatus } from './api'
import {
  ConfirmDialog,
  EmptyState,
  ErrorState,
  MonitorForm,
  SkeletonRows,
  StatusBadge,
} from './components'
import { Icons } from './icons'
import ReliabilityChart from './ReliabilityChart'
import { useRealtime } from './useRealtime'
import type {
  Incident,
  IncidentStatus,
  MetricWindow,
  Monitor,
  MonitorAnalyticsSummary,
  MonitorInput,
  MonitorStatus,
} from './types'

const monitorKey = ['monitors'] as const

function Layout({
  children,
  onAdd,
  owner,
  onLogout,
  loggingOut,
}: {
  children: ReactNode
  onAdd: () => void
  owner: NonNullable<AuthStatus['owner']>
  onLogout: () => void
  loggingOut: boolean
}) {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Link className="brand" to="/" aria-label="HomeLab Monitor dashboard">
          <span className="brand-mark">HM</span>
          <span>
            HomeLab
            <br />
            <small>Monitor</small>
          </span>
        </Link>
        <nav aria-label="Primary navigation">
          <NavLink to="/" end>
            <Icons.dashboard />
            Dashboard
          </NavLink>
          <NavLink to="/services">
            <Icons.services />
            Services
          </NavLink>
          <NavLink to="/incidents">
            <Icons.incidents />
            Incidents
          </NavLink>
          <NavLink to="/analytics">
            <Icons.analytics />
            Analytics
          </NavLink>
        </nav>
      </aside>
      <div className="main-column">
        <header className="topbar">
          <div className="mobile-brand">
            <span className="brand-mark">HM</span>
            <strong>HomeLab Monitor</strong>
          </div>
          <div className="topbar-actions">
            <span className="owner-name" title={owner.email}>
              {owner.displayName}
            </span>
            <button className="button tertiary compact" disabled={loggingOut} onClick={onLogout}>
              {loggingOut ? 'Signing out…' : 'Sign out'}
            </button>
            <button className="button primary compact" onClick={onAdd}>
              <Icons.plus />
              Add monitor
            </button>
          </div>
        </header>
        <main className="content">{children}</main>
      </div>
    </div>
  )
}

function useMonitors() {
  return useQuery({ queryKey: monitorKey, queryFn: api.listMonitors, refetchInterval: 15_000 })
}

function relativeTime(value: string | null) {
  if (!value) return 'Not scheduled'
  const seconds = Math.round((new Date(value).getTime() - Date.now()) / 1000)
  if (Math.abs(seconds) < 60) return seconds <= 0 ? 'Due now' : `in ${seconds}s`
  const minutes = Math.round(seconds / 60)
  return minutes <= 0 ? `${Math.abs(minutes)}m ago` : `in ${minutes}m`
}

function targetLabel(monitor: Monitor) {
  return monitor.type === 'TCP' ? `${monitor.target}:${monitor.port}` : monitor.target
}

function DashboardPage({ add }: { add: () => void }) {
  const monitors = useMonitors()
  const analytics = useQuery({
    queryKey: ['analytics', '24h'],
    queryFn: () => api.getAnalytics('24h'),
    refetchInterval: 60_000,
  })
  if (monitors.isPending)
    return (
      <>
        <PageHeading
          eyebrow="Overview"
          title="Dashboard"
          subtitle="A live view of the services that keep your home lab running."
        />
        <SkeletonRows />
      </>
    )
  if (monitors.isError) return <ErrorState retry={() => void monitors.refetch()} />
  const counts = Object.fromEntries(
    ['ONLINE', 'DEGRADED', 'OFFLINE', 'UNKNOWN', 'PAUSED'].map((status) => [
      status,
      monitors.data.filter((m) => m.status === status).length,
    ]),
  ) as Record<MonitorStatus, number>
  const attention = monitors.data.filter((m) => m.status === 'OFFLINE' || m.status === 'DEGRADED')
  return (
    <>
      <PageHeading
        eyebrow="Overview"
        title="Dashboard"
        subtitle="A live view of the services that keep your home lab running."
      />
      {monitors.data.length === 0 ? (
        <EmptyState
          title="Your lab is quiet"
          action={
            <button className="button primary" onClick={add}>
              <Icons.plus />
              Add your first monitor
            </button>
          }
        >
          Add your first HTTP or TCP monitor to start building a clear operational picture.
        </EmptyState>
      ) : (
        <>
          <section className="metric-grid" aria-label="Service status summary">
            <Metric label="Total services" value={monitors.data.length} accent="neutral" />
            <Metric label="Online" value={counts.ONLINE} accent="online" />
            <Metric
              label="Needs attention"
              value={counts.OFFLINE + counts.DEGRADED}
              accent="attention"
            />
            <Metric
              label="Paused / unknown"
              value={counts.PAUSED + counts.UNKNOWN}
              accent="muted"
            />
            <Metric
              label="Average uptime"
              value={
                analytics.isPending
                  ? 'Loading…'
                  : analytics.isError
                    ? 'Unavailable'
                    : percent(analytics.data.averageMonitorUptimePercent)
              }
              accent="online"
              unit="last 24 hours"
            />
            <Metric
              label="Average latency"
              value={
                analytics.isPending
                  ? 'Loading…'
                  : analytics.isError
                    ? 'Unavailable'
                    : milliseconds(analytics.data.averageLatencyMillis)
              }
              accent="neutral"
              unit="reachable checks"
            />
          </section>
          {analytics.isError ? (
            <p className="panel-action-error" role="alert">
              Reliability summary could not be loaded.{' '}
              <button onClick={() => void analytics.refetch()}>Try again</button>
            </p>
          ) : null}
          <section className="panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Priority view</p>
                <h2>Needs attention</h2>
              </div>
              <Link to="/services">
                View all services <Icons.chevron />
              </Link>
            </div>
            {attention.length === 0 ? (
              <div className="all-clear">
                <span aria-hidden="true">✓</span>
                <div>
                  <strong>Everything looks steady</strong>
                  <p>No services are offline or degraded right now.</p>
                </div>
              </div>
            ) : (
              <ServiceRows monitors={attention.slice(0, 5)} />
            )}
          </section>
          <section className="panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">All services</p>
                <h2>Recent monitor state</h2>
              </div>
              <span className="muted-copy">Refreshes every 15 seconds</span>
            </div>
            <ServiceRows monitors={monitors.data.slice(0, 6)} />
          </section>
        </>
      )}
    </>
  )
}

function Metric({
  label,
  value,
  accent,
  unit = 'services',
}: {
  label: string
  value: number | string
  accent: string
  unit?: string
}) {
  return (
    <article className={`metric metric-${accent}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{unit}</small>
    </article>
  )
}

function ServiceRows({ monitors }: { monitors: Monitor[] }) {
  return (
    <div className="service-rows">
      {monitors.map((monitor) => (
        <Link className="service-row" to={`/services/${monitor.id}`} key={monitor.id}>
          <div className="service-identity">
            <span className={`type-mark type-${monitor.type.toLowerCase()}`}>{monitor.type}</span>
            <div>
              <strong>{monitor.name}</strong>
              <small>{targetLabel(monitor)}</small>
            </div>
          </div>
          <StatusBadge status={monitor.status} />
          <span className="next-check">{relativeTime(monitor.nextCheckAt)}</span>
          <Icons.chevron className="row-chevron" />
        </Link>
      ))}
    </div>
  )
}

function ServicesPage({ add, edit }: { add: () => void; edit: (monitor: Monitor) => void }) {
  const monitors = useMonitors()
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<'ALL' | MonitorStatus>('ALL')
  const [sort, setSort] = useState('name')
  const filtered = useMemo(() => {
    if (!monitors.data) return []
    return monitors.data
      .filter((m) => status === 'ALL' || m.status === status)
      .filter((m) => `${m.name} ${m.target}`.toLowerCase().includes(query.toLowerCase()))
      .toSorted((a, b) =>
        sort === 'status'
          ? a.status.localeCompare(b.status)
          : sort === 'type'
            ? a.type.localeCompare(b.type)
            : a.name.localeCompare(b.name),
      )
  }, [monitors.data, query, sort, status])
  return (
    <>
      <PageHeading
        eyebrow="Inventory"
        title="Services"
        subtitle="Configure checks, inspect current state, and run verification on demand."
      />
      <div className="toolbar">
        <label className="search">
          <span className="sr-only">Search services</span>
          <Icons.search />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by name or target"
          />
        </label>
        <label>
          <span className="sr-only">Filter status</span>
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value as 'ALL' | MonitorStatus)}
          >
            <option value="ALL">All statuses</option>
            <option>ONLINE</option>
            <option>DEGRADED</option>
            <option>OFFLINE</option>
            <option>UNKNOWN</option>
            <option>PAUSED</option>
          </select>
        </label>
        <label>
          <span className="sr-only">Sort services</span>
          <select value={sort} onChange={(e) => setSort(e.target.value)}>
            <option value="name">Sort: name</option>
            <option value="status">Sort: status</option>
            <option value="type">Sort: type</option>
          </select>
        </label>
      </div>
      {monitors.isPending ? (
        <SkeletonRows />
      ) : monitors.isError ? (
        <ErrorState retry={() => void monitors.refetch()} />
      ) : monitors.data.length === 0 ? (
        <EmptyState
          title="No monitors yet"
          action={
            <button className="button primary" onClick={add}>
              <Icons.plus />
              Add monitor
            </button>
          }
        >
          Add a service to begin checking availability and response time.
        </EmptyState>
      ) : filtered.length === 0 ? (
        <EmptyState title="No matching services">
          Try a different search or status filter.
        </EmptyState>
      ) : (
        <div className="service-grid">
          {filtered.map((monitor) => (
            <ServiceCard key={monitor.id} monitor={monitor} edit={() => edit(monitor)} />
          ))}
        </div>
      )}
    </>
  )
}

function incidentLabel(value: string) {
  return value.replaceAll('_', ' ').toLowerCase()
}

function percent(value: number | null) {
  return value === null ? 'Insufficient data' : `${value.toFixed(2)}%`
}

function milliseconds(value: number | null) {
  return value === null ? 'Insufficient data' : `${value.toFixed(1)} ms`
}

function duration(value: number) {
  const minutes = Math.round(value / 60_000)
  if (minutes < 60) return `${minutes}m`
  const hours = Math.round((minutes / 60) * 10) / 10
  return hours < 48 ? `${hours}h` : `${Math.round((hours / 24) * 10) / 10}d`
}

function WindowPicker({
  value,
  onChange,
}: {
  value: MetricWindow
  onChange: (value: MetricWindow) => void
}) {
  return (
    <label className="window-picker">
      <span>Time range</span>
      <select value={value} onChange={(event) => onChange(event.target.value as MetricWindow)}>
        <option value="1h">Last hour</option>
        <option value="24h">Last 24 hours</option>
        <option value="7d">Last 7 days</option>
        <option value="30d">Last 30 days</option>
      </select>
    </label>
  )
}

function AnalyticsTable({
  title,
  monitors,
  value,
}: {
  title: string
  monitors: MonitorAnalyticsSummary[]
  value: (monitor: MonitorAnalyticsSummary) => string
}) {
  return (
    <section className="panel analytics-ranking">
      <div className="panel-heading">
        <h2>{title}</h2>
      </div>
      {monitors.length ? (
        <ol>
          {monitors.map((monitor) => (
            <li key={monitor.monitorId}>
              <Link to={`/services/${monitor.monitorId}`}>{monitor.monitorName}</Link>
              <strong>{value(monitor)}</strong>
            </li>
          ))}
        </ol>
      ) : (
        <p className="panel-empty">No measured data in this range.</p>
      )}
    </section>
  )
}

function AnalyticsPage() {
  const [window, setWindow] = useState<MetricWindow>('24h')
  const analytics = useQuery({
    queryKey: ['analytics', window],
    queryFn: () => api.getAnalytics(window),
    refetchInterval: 60_000,
  })
  return (
    <>
      <PageHeading
        eyebrow="Measured reliability"
        title="Analytics"
        subtitle="Duration-based availability and reachable-check latency, with unobserved time excluded."
        action={<WindowPicker value={window} onChange={setWindow} />}
      />
      {analytics.isPending ? (
        <SkeletonRows />
      ) : analytics.isError ? (
        <ErrorState retry={() => void analytics.refetch()} />
      ) : analytics.data.monitors.length === 0 ? (
        <EmptyState
          title="No analytics yet"
          action={
            <Link className="button primary" to="/services">
              Add a monitor
            </Link>
          }
        >
          Add a service and complete a check to begin measuring reliability and latency.
        </EmptyState>
      ) : (
        <>
          {analytics.data.partial ? (
            <p className="data-notice" role="status">
              This range is partially limited by raw-check retention; unobserved time is excluded.
            </p>
          ) : null}
          <section className="metric-grid analytics-metrics" aria-label="Analytics summary">
            <Metric
              label="Overall uptime"
              value={percent(analytics.data.overallUptimePercent)}
              accent="online"
              unit={window}
            />
            <Metric
              label="Average monitor"
              value={percent(analytics.data.averageMonitorUptimePercent)}
              accent="neutral"
              unit="equal monitor weight"
            />
            <Metric
              label="Average latency"
              value={milliseconds(analytics.data.averageLatencyMillis)}
              accent="neutral"
              unit="reachable checks"
            />
            <Metric
              label="Incidents"
              value={analytics.data.incidentCount}
              accent="attention"
              unit={window}
            />
          </section>
          <div className="analytics-grid">
            <AnalyticsTable
              title="Least reliable"
              monitors={analytics.data.leastReliableMonitors}
              value={(monitor) => percent(monitor.uptimePercent)}
            />
            <AnalyticsTable
              title="Slowest services"
              monitors={analytics.data.slowestMonitors}
              value={(monitor) => milliseconds(monitor.averageLatencyMillis)}
            />
            <AnalyticsTable
              title="Most downtime"
              monitors={analytics.data.mostDowntimeMonitors}
              value={(monitor) => duration(monitor.downtimeMillis)}
            />
          </div>
          <section className="panel analytics-all">
            <div className="panel-heading">
              <h2>All measured services</h2>
              <span className="muted-copy">Excluded time is never counted as up or down</span>
            </div>
            <div className="analytics-table" role="table" aria-label="All monitor analytics">
              <div role="row" className="analytics-table-head">
                <span role="columnheader">Service</span>
                <span role="columnheader">Uptime</span>
                <span role="columnheader">Latency</span>
                <span role="columnheader">Downtime</span>
              </div>
              {analytics.data.monitors.map((monitor) => (
                <div role="row" key={monitor.monitorId}>
                  <Link role="cell" to={`/services/${monitor.monitorId}`}>
                    {monitor.monitorName}
                  </Link>
                  <span role="cell">{percent(monitor.uptimePercent)}</span>
                  <span role="cell">{milliseconds(monitor.averageLatencyMillis)}</span>
                  <span role="cell">{duration(monitor.downtimeMillis)}</span>
                </div>
              ))}
            </div>
          </section>
        </>
      )}
    </>
  )
}

function IncidentRows({ incidents, monitors }: { incidents: Incident[]; monitors: Monitor[] }) {
  const names = new Map(monitors.map((monitor) => [monitor.id, monitor.name]))
  return (
    <div className="incident-list">
      {incidents.map((incident) => (
        <article key={incident.id} className="incident-row">
          <span className={`incident-state incident-${incident.status.toLowerCase()}`}>
            {incident.status}
          </span>
          <div>
            <strong>{names.get(incident.monitorId) ?? 'Deleted monitor'}</strong>
            <p>{incidentLabel(incident.outageReason)}</p>
          </div>
          <div className="incident-time">
            <time>{new Date(incident.startedAt).toLocaleString()}</time>
            <small>
              {incident.endedAt
                ? `${incidentLabel(incident.resolutionReason ?? 'resolved')} · ${new Date(incident.endedAt).toLocaleString()}`
                : 'Ongoing'}
            </small>
          </div>
          {names.has(incident.monitorId) ? (
            <Link className="button tertiary" to={`/services/${incident.monitorId}`}>
              View service
            </Link>
          ) : null}
        </article>
      ))}
    </div>
  )
}

function PageControls({
  page,
  totalPages,
  onChange,
  label,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
  label: string
}) {
  if (totalPages <= 1 && page === 0) return null
  return (
    <nav className="page-controls" aria-label={label}>
      <button className="button tertiary" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <button
        className="button tertiary"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Next
      </button>
    </nav>
  )
}

function IncidentsPage() {
  const [status, setStatus] = useState<'ALL' | IncidentStatus>('ALL')
  const [page, setPage] = useState(0)
  const monitors = useMonitors()
  const incidents = useQuery({
    queryKey: ['incidents', status, page],
    queryFn: () => api.getIncidents({ ...(status === 'ALL' ? {} : { status }), page }),
    refetchInterval: 15_000,
  })
  const activeIncidents = useQuery({
    queryKey: ['incidents', 'active-count'],
    queryFn: () => api.getIncidents({ status: 'ACTIVE', size: 1 }),
    refetchInterval: 15_000,
  })
  useEffect(() => {
    if (!incidents.data || page === 0 || page < incidents.data.totalPages) return
    const adjustment = window.setTimeout(
      () => setPage(Math.max(0, incidents.data.totalPages - 1)),
      0,
    )
    return () => window.clearTimeout(adjustment)
  }, [incidents.data, page])
  return (
    <>
      <PageHeading
        eyebrow="Reliability"
        title="Incidents"
        subtitle="Confirmed outages open at the failure threshold and close only after recovery or a deliberate pause."
      />
      <div className="incident-summary">
        <div>
          <span>Active incidents</span>
          <strong>{activeIncidents.data?.totalElements ?? '—'}</strong>
        </div>
        <label>
          <span className="sr-only">Filter incidents</span>
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as 'ALL' | IncidentStatus)
              setPage(0)
            }}
          >
            <option value="ALL">All incidents</option>
            <option value="ACTIVE">Active only</option>
            <option value="RESOLVED">Resolved only</option>
          </select>
        </label>
      </div>
      {incidents.isPending || monitors.isPending || activeIncidents.isPending ? (
        <SkeletonRows />
      ) : incidents.isError || monitors.isError || activeIncidents.isError ? (
        <ErrorState
          retry={() =>
            void Promise.all([incidents.refetch(), monitors.refetch(), activeIncidents.refetch()])
          }
        />
      ) : incidents.data.content.length === 0 && page === 0 ? (
        <EmptyState title={status === 'ACTIVE' ? 'No active incidents' : 'No incidents recorded'}>
          {status === 'ACTIVE'
            ? 'No service is currently in a confirmed outage.'
            : 'Confirmed outages will appear here after a monitor reaches its failure threshold.'}
        </EmptyState>
      ) : (
        <>
          <IncidentRows incidents={incidents.data.content} monitors={monitors.data} />
          <PageControls
            page={incidents.data.page}
            totalPages={incidents.data.totalPages}
            onChange={setPage}
            label="Incident pages"
          />
        </>
      )}
    </>
  )
}

function ServiceCard({ monitor, edit }: { monitor: Monitor; edit: () => void }) {
  return (
    <article className="service-card">
      <div className="card-top">
        <span className={`type-mark type-${monitor.type.toLowerCase()}`}>{monitor.type}</span>
        <StatusBadge status={monitor.status} />
      </div>
      <h2>
        <Link to={`/services/${monitor.id}`}>{monitor.name}</Link>
      </h2>
      <p>{monitor.description || 'No description provided.'}</p>
      <dl>
        <div>
          <dt>Target</dt>
          <dd>{targetLabel(monitor)}</dd>
        </div>
        <div>
          <dt>Interval</dt>
          <dd>{monitor.intervalSeconds}s</dd>
        </div>
        <div>
          <dt>Next check</dt>
          <dd>{relativeTime(monitor.nextCheckAt)}</dd>
        </div>
      </dl>
      <div className="card-actions">
        <Link className="button tertiary" to={`/services/${monitor.id}`}>
          View details
        </Link>
        <button className="icon-button" onClick={edit} aria-label={`Edit ${monitor.name}`}>
          <Icons.edit />
        </button>
      </div>
    </article>
  )
}

function ServiceDetailPage({ edit }: { edit: (monitor: Monitor) => void }) {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const client = useQueryClient()
  const [incidentPage, setIncidentPage] = useState(0)
  const [metricWindow, setMetricWindow] = useState<MetricWindow>('24h')
  const monitor = useQuery({
    queryKey: ['monitor', id],
    queryFn: () => api.getMonitor(id),
    refetchInterval: 15_000,
  })
  const checks = useQuery({
    queryKey: ['checks', id],
    queryFn: () => api.getChecks(id),
    enabled: monitor.isSuccess,
    refetchInterval: 15_000,
  })
  const history = useQuery({
    queryKey: ['history', id],
    queryFn: () => api.getHistory(id),
    enabled: monitor.isSuccess,
    refetchInterval: 15_000,
  })
  const incidents = useQuery({
    queryKey: ['incidents', id, incidentPage],
    queryFn: () => api.getIncidents({ monitorId: id, page: incidentPage, size: 10 }),
    enabled: monitor.isSuccess,
    refetchInterval: 15_000,
  })
  const selectedMetrics = useQuery({
    queryKey: ['metrics', id, metricWindow],
    queryFn: () => api.getMonitorMetrics(id, metricWindow),
    enabled: monitor.isSuccess,
    refetchInterval: 60_000,
  })
  const metrics24h = useQuery({
    queryKey: ['metrics', id, '24h'],
    queryFn: () => api.getMonitorMetrics(id, '24h'),
    enabled: monitor.isSuccess,
    refetchInterval: 60_000,
  })
  const metrics7d = useQuery({
    queryKey: ['metrics', id, '7d'],
    queryFn: () => api.getMonitorMetrics(id, '7d'),
    enabled: monitor.isSuccess,
    refetchInterval: 60_000,
  })
  const metrics30d = useQuery({
    queryKey: ['metrics', id, '30d'],
    queryFn: () => api.getMonitorMetrics(id, '30d'),
    enabled: monitor.isSuccess,
    refetchInterval: 60_000,
  })
  useEffect(() => {
    if (!incidents.data || incidentPage === 0 || incidentPage < incidents.data.totalPages) return
    const adjustment = window.setTimeout(
      () => setIncidentPage(Math.max(0, incidents.data.totalPages - 1)),
      0,
    )
    return () => window.clearTimeout(adjustment)
  }, [incidentPage, incidents.data])
  const [confirmDelete, setConfirmDelete] = useState(false)
  const check = useMutation({
    mutationFn: () => api.checkMonitor(id),
    onSuccess: () =>
      void Promise.all([
        client.invalidateQueries({ queryKey: ['monitor', id] }),
        client.invalidateQueries({ queryKey: ['checks', id] }),
        client.invalidateQueries({ queryKey: ['history', id] }),
        client.invalidateQueries({ queryKey: ['incidents'] }),
        client.invalidateQueries({ queryKey: ['metrics', id] }),
        client.invalidateQueries({ queryKey: ['analytics'] }),
        client.invalidateQueries({ queryKey: monitorKey }),
      ]),
  })
  const update = useMutation({
    mutationFn: (input: MonitorInput) => api.updateMonitor(id, input),
    onSuccess: () =>
      void Promise.all([
        client.invalidateQueries({ queryKey: ['monitor', id] }),
        client.invalidateQueries({ queryKey: ['history', id] }),
        client.invalidateQueries({ queryKey: ['incidents'] }),
        client.invalidateQueries({ queryKey: ['metrics', id] }),
        client.invalidateQueries({ queryKey: ['analytics'] }),
        client.invalidateQueries({ queryKey: monitorKey }),
      ]),
  })
  const remove = useMutation({
    mutationFn: () => api.deleteMonitor(id),
    onSuccess: () => {
      void Promise.all([
        client.invalidateQueries({ queryKey: monitorKey }),
        client.invalidateQueries({ queryKey: ['analytics'] }),
      ])
      navigate('/services')
    },
  })
  if (monitor.isPending) return <SkeletonRows />
  if (monitor.isError) return <ErrorState retry={() => void monitor.refetch()} />
  const value = monitor.data
  const input: MonitorInput = {
    name: value.name,
    description: value.description,
    type: value.type,
    target: value.target,
    port: value.port,
    enabled: !value.enabled,
    intervalSeconds: value.intervalSeconds,
    timeoutMillis: value.timeoutMillis,
    failureThreshold: value.failureThreshold,
    recoveryThreshold: value.recoveryThreshold,
    latencyWarningMillis: value.latencyWarningMillis,
    expectedHttpStatus: value.expectedHttpStatus,
  }
  return (
    <>
      <Link className="back-link" to="/services">
        ← Back to services
      </Link>
      <div className="detail-heading">
        <div>
          <div className="detail-title">
            <span className={`type-mark type-${value.type.toLowerCase()}`}>{value.type}</span>
            <h1>{value.name}</h1>
            <StatusBadge status={value.status} />
          </div>
          <p>{value.description || targetLabel(value)}</p>
        </div>
        <div className="detail-actions">
          <button className="button secondary" onClick={() => edit(value)}>
            <Icons.edit />
            Edit
          </button>
          <button
            className="button secondary"
            disabled={check.isPending || !value.enabled}
            onClick={() => check.mutate()}
          >
            <Icons.refresh />
            {check.isPending ? 'Checking…' : 'Check now'}
          </button>
        </div>
      </div>
      {check.isError ? (
        <p className="inline-error" role="alert">
          {message(check.error)}
        </p>
      ) : check.isSuccess ? (
        <p className="inline-success" role="status">
          Check completed: {check.data.result.replaceAll('_', ' ').toLowerCase()}.
        </p>
      ) : null}
      <section className="panel reliability-panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Measured availability</p>
            <h2>Reliability</h2>
          </div>
          <WindowPicker value={metricWindow} onChange={setMetricWindow} />
        </div>
        {selectedMetrics.isPending ||
        metrics24h.isPending ||
        metrics7d.isPending ||
        metrics30d.isPending ? (
          <SkeletonRows />
        ) : selectedMetrics.isError ||
          metrics24h.isError ||
          metrics7d.isError ||
          metrics30d.isError ? (
          <p className="panel-action-error" role="alert">
            Reliability metrics could not be loaded.{' '}
            <button
              onClick={() =>
                void Promise.all([
                  selectedMetrics.refetch(),
                  metrics24h.refetch(),
                  metrics7d.refetch(),
                  metrics30d.refetch(),
                ])
              }
            >
              Try again
            </button>
          </p>
        ) : (
          <>
            <div className="uptime-cards">
              <div>
                <span>24 hours</span>
                <strong>{percent(metrics24h.data.uptimePercent)}</strong>
                {metrics24h.data.partial ? <small>Retention-limited</small> : null}
              </div>
              <div>
                <span>7 days</span>
                <strong>{percent(metrics7d.data.uptimePercent)}</strong>
                {metrics7d.data.partial ? <small>Retention-limited</small> : null}
              </div>
              <div>
                <span>30 days</span>
                <strong>{percent(metrics30d.data.uptimePercent)}</strong>
                {metrics30d.data.partial ? <small>Retention-limited</small> : null}
              </div>
              <div>
                <span>p95 latency</span>
                <strong>{milliseconds(selectedMetrics.data.latency.p95Millis)}</strong>
              </div>
            </div>
            {selectedMetrics.data.partial ||
            metrics24h.data.partial ||
            metrics7d.data.partial ||
            metrics30d.data.partial ? (
              <p className="data-notice">
                Retention limits this range; unobserved time remains excluded.
              </p>
            ) : null}
            <ReliabilityChart buckets={selectedMetrics.data.buckets} />
            <dl className="latency-facts">
              <div>
                <dt>Samples</dt>
                <dd>{selectedMetrics.data.latency.sampleCount}</dd>
              </div>
              <div>
                <dt>Average</dt>
                <dd>{milliseconds(selectedMetrics.data.latency.averageMillis)}</dd>
              </div>
              <div>
                <dt>Minimum</dt>
                <dd>{milliseconds(selectedMetrics.data.latency.minMillis)}</dd>
              </div>
              <div>
                <dt>Median</dt>
                <dd>{milliseconds(selectedMetrics.data.latency.medianMillis)}</dd>
              </div>
              <div>
                <dt>Maximum</dt>
                <dd>{milliseconds(selectedMetrics.data.latency.maxMillis)}</dd>
              </div>
              <div>
                <dt>Excluded</dt>
                <dd>{duration(selectedMetrics.data.excludedMillis)}</dd>
              </div>
            </dl>
          </>
        )}
      </section>
      <section className="detail-grid">
        <div className="panel facts">
          <div className="panel-heading">
            <h2>Configuration</h2>
          </div>
          <dl>
            <div>
              <dt>Target</dt>
              <dd>{targetLabel(value)}</dd>
            </div>
            <div>
              <dt>Interval</dt>
              <dd>{value.intervalSeconds} seconds</dd>
            </div>
            <div>
              <dt>Timeout</dt>
              <dd>{value.timeoutMillis} ms</dd>
            </div>
            <div>
              <dt>Failure / recovery</dt>
              <dd>
                {value.failureThreshold} / {value.recoveryThreshold}
              </dd>
            </div>
            <div>
              <dt>Latency warning</dt>
              <dd>{value.latencyWarningMillis ? `${value.latencyWarningMillis} ms` : 'Not set'}</dd>
            </div>
          </dl>
          <div className="configuration-actions">
            <button
              className="button tertiary"
              disabled={update.isPending}
              onClick={() => update.mutate(input)}
            >
              {value.enabled ? 'Pause monitoring' : 'Resume monitoring'}
            </button>
            <button className="text-danger" onClick={() => setConfirmDelete(true)}>
              <Icons.trash />
              Delete service
            </button>
          </div>
          {update.isError ? (
            <p className="panel-action-error" role="alert">
              {message(update.error)}
            </p>
          ) : null}
          {update.isSuccess ? (
            <p className="inline-success" role="status">
              Monitoring {update.data.enabled ? 'resumed' : 'paused'}.
            </p>
          ) : null}
        </div>
        <div className="panel">
          <div className="panel-heading">
            <h2>Recent checks</h2>
            <span className="muted-copy">Latest 20</span>
          </div>
          {checks.isPending ? (
            <SkeletonRows />
          ) : checks.isError ? (
            <p className="panel-action-error" role="alert">
              Checks could not be loaded.{' '}
              <button onClick={() => void checks.refetch()}>Try again</button>
            </p>
          ) : checks.data?.content.length ? (
            <div className="timeline">
              {checks.data.content.map((item) => (
                <div key={item.id}>
                  <span className={item.result === 'SUCCESS' ? 'timeline-good' : 'timeline-bad'} />
                  <div>
                    <strong>{item.result.replaceAll('_', ' ')}</strong>
                    <small>
                      {new Date(item.checkedAt).toLocaleString()} ·{' '}
                      {item.responseTimeMillis === null
                        ? 'No latency'
                        : `${item.responseTimeMillis} ms`}
                    </small>
                    {item.errorMessage ? <p>{item.errorMessage}</p> : null}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="panel-empty">No checks recorded yet.</p>
          )}
        </div>
      </section>
      <section className="panel">
        <div className="panel-heading">
          <h2>State history</h2>
          <span className="muted-copy">Authoritative transitions</span>
        </div>
        {history.isPending ? (
          <SkeletonRows />
        ) : history.isError ? (
          <p className="panel-action-error" role="alert">
            State history could not be loaded.{' '}
            <button onClick={() => void history.refetch()}>Try again</button>
          </p>
        ) : history.data?.content.length ? (
          <div className="history-list">
            {history.data.content.map((item) => (
              <div key={item.id}>
                <time>{new Date(item.effectiveAt).toLocaleString()}</time>
                <span>
                  {item.fromStatus ?? 'CREATED'} → <strong>{item.toStatus}</strong>
                </span>
                <small>{item.reason.replaceAll('_', ' ')}</small>
              </div>
            ))}
          </div>
        ) : (
          <p className="panel-empty">No state transitions recorded.</p>
        )}
      </section>
      <section className="panel">
        <div className="panel-heading">
          <h2>Incidents</h2>
          <Link to="/incidents">View all incidents</Link>
        </div>
        {incidents.isPending ? (
          <SkeletonRows />
        ) : incidents.isError ? (
          <p className="panel-action-error" role="alert">
            Incidents could not be loaded.{' '}
            <button onClick={() => void incidents.refetch()}>Try again</button>
          </p>
        ) : incidents.data && (incidents.data.content.length > 0 || incidentPage > 0) ? (
          <>
            <IncidentRows incidents={incidents.data.content} monitors={[value]} />
            <PageControls
              page={incidents.data.page}
              totalPages={incidents.data.totalPages}
              onChange={setIncidentPage}
              label="Service incident pages"
            />
          </>
        ) : (
          <p className="panel-empty">No confirmed outages recorded.</p>
        )}
      </section>
      {confirmDelete ? (
        <ConfirmDialog
          title={`Delete ${value.name}?`}
          busy={remove.isPending}
          error={remove.isError ? message(remove.error) : undefined}
          confirmLabel="Delete monitor"
          onCancel={() => setConfirmDelete(false)}
          onConfirm={() => remove.mutate()}
        >
          This permanently removes the monitor and its check, state, and incident history.
        </ConfirmDialog>
      ) : null}
    </>
  )
}

function PageHeading({
  eyebrow,
  title,
  subtitle,
  action,
}: {
  eyebrow: string
  title: string
  subtitle: string
  action?: ReactNode
}) {
  return (
    <header className="page-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
      {action}
    </header>
  )
}

function message(error: Error) {
  return error instanceof ApiError ? error.message : 'The request could not be completed.'
}

function MonitoringApp({
  auth,
  onLogout,
  loggingOut,
}: {
  auth: AuthStatus & { owner: NonNullable<AuthStatus['owner']> }
  onLogout: () => void
  loggingOut: boolean
}) {
  const client = useQueryClient()
  useRealtime()
  const [form, setForm] = useState<{ open: boolean; monitor?: Monitor }>({ open: false })
  const [notice, setNotice] = useState<string>()
  const save = useMutation({
    mutationFn: (input: MonitorInput) =>
      form.monitor ? api.updateMonitor(form.monitor.id, input) : api.createMonitor(input),
    onSuccess: (monitor) => {
      setNotice(form.monitor ? `${monitor.name} updated.` : `${monitor.name} created.`)
      setForm({ open: false })
      void client.invalidateQueries({ queryKey: monitorKey })
      void client.invalidateQueries({ queryKey: ['monitor', monitor.id] })
      void client.invalidateQueries({ queryKey: ['checks', monitor.id] })
      void client.invalidateQueries({ queryKey: ['history', monitor.id] })
      void client.invalidateQueries({ queryKey: ['metrics', monitor.id] })
      void client.invalidateQueries({ queryKey: ['analytics'] })
    },
  })
  return (
    <Layout
      onAdd={() => setForm({ open: true })}
      owner={auth.owner}
      onLogout={onLogout}
      loggingOut={loggingOut}
    >
      <Routes>
        <Route path="/" element={<DashboardPage add={() => setForm({ open: true })} />} />
        <Route
          path="/services"
          element={
            <ServicesPage
              add={() => setForm({ open: true })}
              edit={(monitor) => setForm({ open: true, monitor })}
            />
          }
        />
        <Route
          path="/services/:id"
          element={<ServiceDetailPage edit={(monitor) => setForm({ open: true, monitor })} />}
        />
        <Route path="/incidents" element={<IncidentsPage />} />
        <Route path="/analytics" element={<AnalyticsPage />} />
        <Route
          path="*"
          element={
            <EmptyState
              title="Page not found"
              action={
                <Link className="button primary" to="/">
                  Go to dashboard
                </Link>
              }
            >
              Return to the dashboard to continue monitoring your lab.
            </EmptyState>
          }
        />
      </Routes>
      {form.open ? (
        <MonitorForm
          monitor={form.monitor}
          busy={save.isPending}
          error={save.isError ? message(save.error) : undefined}
          onCancel={() => {
            save.reset()
            setForm({ open: false })
          }}
          onSubmit={(input) => save.mutate(input)}
        />
      ) : null}
      {notice ? (
        <div className="toast" role="status">
          {notice}
          <button aria-label="Dismiss notification" onClick={() => setNotice(undefined)}>
            ×
          </button>
        </div>
      ) : null}
    </Layout>
  )
}

function AuthScreen({
  setup,
  onAuthenticated,
}: {
  setup: boolean
  onAuthenticated: (auth: AuthStatus) => void
}) {
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const authenticate = useMutation({
    mutationFn: () =>
      setup ? api.setupOwner({ email, displayName, password }) : api.login({ email, password }),
    onSuccess: onAuthenticated,
  })
  const confirmationError = setup && confirmation !== password

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!confirmationError) authenticate.mutate()
  }

  return (
    <main className="auth-shell">
      <section className="auth-card" aria-labelledby="auth-title">
        <Link className="brand auth-brand" to="/" aria-label="HomeLab Monitor">
          <span className="brand-mark">HM</span>
          <span>HomeLab Monitor</span>
        </Link>
        <p className="eyebrow">{setup ? 'First-time setup' : 'Owner access'}</p>
        <h1 id="auth-title">{setup ? 'Secure your monitor' : 'Welcome back'}</h1>
        <p className="auth-intro">
          {setup
            ? 'Create the single owner account used to manage this HomeLab Monitor.'
            : 'Sign in with the owner account to view and manage your services.'}
        </p>
        <form onSubmit={submit}>
          {setup ? (
            <label>
              Display name
              <input
                autoComplete="name"
                maxLength={120}
                required
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
              />
            </label>
          ) : null}
          <label>
            Email
            <input
              autoComplete="email"
              maxLength={254}
              required
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
          <label>
            Password
            <input
              autoComplete={setup ? 'new-password' : 'current-password'}
              minLength={setup ? 12 : undefined}
              maxLength={72}
              required
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {setup ? (
            <label>
              Confirm password
              <input
                autoComplete="new-password"
                minLength={12}
                maxLength={72}
                required
                type="password"
                value={confirmation}
                onChange={(event) => setConfirmation(event.target.value)}
              />
            </label>
          ) : null}
          {confirmationError ? (
            <p className="inline-error" role="alert">
              Passwords do not match.
            </p>
          ) : authenticate.isError ? (
            <p className="inline-error" role="alert">
              {message(authenticate.error)}
            </p>
          ) : null}
          <button
            className="button primary auth-submit"
            disabled={authenticate.isPending || confirmationError}
          >
            {authenticate.isPending
              ? setup
                ? 'Creating owner…'
                : 'Signing in…'
              : setup
                ? 'Create owner account'
                : 'Sign in'}
          </button>
        </form>
      </section>
    </main>
  )
}

export function App() {
  const client = useQueryClient()
  const auth = useQuery({ queryKey: ['auth'], queryFn: api.authStatus, retry: false })
  const logout = useMutation({
    mutationFn: api.logout,
    onSuccess: () => {
      client.clear()
      client.setQueryData<AuthStatus>(['auth'], {
        setupRequired: false,
        authenticated: false,
        owner: null,
      })
    },
  })

  useEffect(() => {
    const requireAuthentication = () => void client.invalidateQueries({ queryKey: ['auth'] })
    window.addEventListener('homelab-auth-required', requireAuthentication)
    return () => window.removeEventListener('homelab-auth-required', requireAuthentication)
  }, [client])

  if (auth.isPending) {
    return (
      <main className="auth-shell">
        <div className="auth-loading" role="status">
          Loading HomeLab Monitor…
        </div>
      </main>
    )
  }
  if (auth.isError) {
    return (
      <main className="auth-shell">
        <ErrorState retry={() => void auth.refetch()} />
      </main>
    )
  }
  if (auth.data.setupRequired || !auth.data.authenticated || !auth.data.owner) {
    return (
      <AuthScreen
        setup={auth.data.setupRequired}
        onAuthenticated={(value) => client.setQueryData(['auth'], value)}
      />
    )
  }
  return (
    <MonitoringApp
      auth={auth.data as AuthStatus & { owner: NonNullable<AuthStatus['owner']> }}
      onLogout={() => logout.mutate()}
      loggingOut={logout.isPending}
    />
  )
}
