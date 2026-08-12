import { useMemo, useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, NavLink, Route, Routes, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from './api'
import {
  ConfirmDialog,
  EmptyState,
  ErrorState,
  MonitorForm,
  SkeletonRows,
  StatusBadge,
} from './components'
import { Icons } from './icons'
import type { Monitor, MonitorInput, MonitorStatus } from './types'

const monitorKey = ['monitors'] as const

function Layout({ children, onAdd }: { children: ReactNode; onAdd: () => void }) {
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
          <span className="nav-planned" aria-disabled="true" title="Available in Phase 5">
            <Icons.incidents />
            Incidents<small>Soon</small>
          </span>
          <span className="nav-planned" aria-disabled="true" title="Available in Phase 6">
            <Icons.analytics />
            Analytics<small>Soon</small>
          </span>
        </nav>
      </aside>
      <div className="main-column">
        <header className="topbar">
          <div className="mobile-brand">
            <span className="brand-mark">HM</span>
            <strong>HomeLab Monitor</strong>
          </div>
          <button className="button primary compact" onClick={onAdd}>
            <Icons.plus />
            Add monitor
          </button>
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
          </section>
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

function Metric({ label, value, accent }: { label: string; value: number; accent: string }) {
  return (
    <article className={`metric metric-${accent}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>services</small>
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
  const [confirmDelete, setConfirmDelete] = useState(false)
  const check = useMutation({
    mutationFn: () => api.checkMonitor(id),
    onSuccess: () =>
      void Promise.all([
        client.invalidateQueries({ queryKey: ['monitor', id] }),
        client.invalidateQueries({ queryKey: ['checks', id] }),
        client.invalidateQueries({ queryKey: ['history', id] }),
        client.invalidateQueries({ queryKey: monitorKey }),
      ]),
  })
  const update = useMutation({
    mutationFn: (input: MonitorInput) => api.updateMonitor(id, input),
    onSuccess: () =>
      void Promise.all([
        client.invalidateQueries({ queryKey: ['monitor', id] }),
        client.invalidateQueries({ queryKey: ['history', id] }),
        client.invalidateQueries({ queryKey: monitorKey }),
      ]),
  })
  const remove = useMutation({
    mutationFn: () => api.deleteMonitor(id),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: monitorKey })
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
      {confirmDelete ? (
        <ConfirmDialog
          title={`Delete ${value.name}?`}
          busy={remove.isPending}
          error={remove.isError ? message(remove.error) : undefined}
          confirmLabel="Delete monitor"
          onCancel={() => setConfirmDelete(false)}
          onConfirm={() => remove.mutate()}
        >
          This permanently removes the monitor and its check and state history.
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

export function App() {
  const client = useQueryClient()
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
    },
  })
  return (
    <Layout onAdd={() => setForm({ open: true })}>
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
