import {
  useEffect,
  useId,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
  type RefObject,
} from 'react'
import { Icons } from './icons'
import type { Monitor, MonitorInput, MonitorStatus, MonitorType } from './types'

export function StatusBadge({ status }: { status: MonitorStatus }) {
  return (
    <span className={`status status-${status.toLowerCase()}`}>
      <span aria-hidden="true" />
      {status}
    </span>
  )
}

export function EmptyState({
  title,
  children,
  action,
}: {
  title: string
  children: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="empty-state">
      <div className="empty-orbit" aria-hidden="true">
        <span />
      </div>
      <h2>{title}</h2>
      <p>{children}</p>
      {action}
    </div>
  )
}

export function ErrorState({ retry }: { retry: () => void }) {
  return (
    <div className="error-state" role="alert">
      <strong>We couldn’t load this view.</strong>
      <span>Check that the backend is running, then try again.</span>
      <button className="button secondary" onClick={retry}>
        Try again
      </button>
    </div>
  )
}

export function SkeletonRows() {
  return (
    <div className="skeleton-list" role="status" aria-label="Loading content">
      <span />
      <span />
      <span />
    </div>
  )
}

type FormProps = {
  monitor?: Monitor
  busy: boolean
  error?: string
  onCancel: () => void
  onSubmit: (input: MonitorInput) => void
}

const defaults: MonitorInput = {
  name: '',
  description: '',
  type: 'HTTP',
  target: '',
  port: null,
  enabled: true,
  intervalSeconds: 60,
  timeoutMillis: 5000,
  failureThreshold: 3,
  recoveryThreshold: 2,
  latencyWarningMillis: 1000,
  expectedHttpStatus: 200,
}

function useModal(
  onCancel: () => void,
  dialog: RefObject<HTMLElement | null>,
  initialFocus: string,
) {
  const cancel = useRef(onCancel)
  useEffect(() => {
    cancel.current = onCancel
  }, [onCancel])
  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const overflow = document.body.style.overflow
    const backdrop = dialog.current?.closest('.drawer-backdrop')
    const background = backdrop?.parentElement
      ? Array.from(backdrop.parentElement.children).filter((element) => element !== backdrop)
      : []
    document.body.style.overflow = 'hidden'
    background.forEach((element) => element.setAttribute('inert', ''))
    const keyboard = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        cancel.current()
        return
      }
      if (event.key !== 'Tab' || !dialog.current) return
      const focusable = Array.from(
        dialog.current.querySelectorAll<HTMLElement>(
          'button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), a[href]',
        ),
      )
      const first = focusable[0]
      const last = focusable.at(-1)
      if (!first || !last) return
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    window.addEventListener('keydown', keyboard)
    dialog.current?.querySelector<HTMLElement>(initialFocus)?.focus()
    return () => {
      window.removeEventListener('keydown', keyboard)
      document.body.style.overflow = overflow
      background.forEach((element) => element.removeAttribute('inert'))
      previous?.focus()
    }
  }, [dialog, initialFocus])
}

function inputFrom(monitor?: Monitor): MonitorInput {
  if (!monitor) return defaults
  return {
    name: monitor.name,
    description: monitor.description,
    type: monitor.type,
    target: monitor.target,
    port: monitor.port,
    enabled: monitor.enabled,
    intervalSeconds: monitor.intervalSeconds,
    timeoutMillis: monitor.timeoutMillis,
    failureThreshold: monitor.failureThreshold,
    recoveryThreshold: monitor.recoveryThreshold,
    latencyWarningMillis: monitor.latencyWarningMillis,
    expectedHttpStatus: monitor.expectedHttpStatus,
  }
}

export function MonitorForm({ monitor, busy, error, onCancel, onSubmit }: FormProps) {
  const dialog = useRef<HTMLElement>(null)
  useModal(onCancel, dialog, '[data-initial-focus]')
  const [form, setForm] = useState<MonitorInput>(() => inputFrom(monitor))
  const titleId = useId()
  const set = <K extends keyof MonitorInput>(key: K, value: MonitorInput[K]) =>
    setForm((old) => ({ ...old, [key]: value }))
  const number = (value: string) => (value === '' ? null : Number(value))
  const submit = (event: FormEvent) => {
    event.preventDefault()
    onSubmit({
      ...form,
      port: form.type === 'TCP' ? form.port : null,
      expectedHttpStatus: form.type === 'HTTP' ? form.expectedHttpStatus : null,
    })
  }

  return (
    <div
      className="drawer-backdrop"
      role="presentation"
      onMouseDown={(event) => event.target === event.currentTarget && onCancel()}
    >
      <section
        ref={dialog}
        className="drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <div className="drawer-heading">
          <div>
            <p className="eyebrow">Service configuration</p>
            <h2 id={titleId}>{monitor ? 'Edit monitor' : 'Add a monitor'}</h2>
          </div>
          <button className="icon-button" aria-label="Close" onClick={onCancel}>
            <Icons.close />
          </button>
        </div>
        <form onSubmit={submit}>
          <label>
            Name
            <input
              required
              maxLength={120}
              data-initial-focus
              value={form.name}
              onChange={(e) => set('name', e.target.value)}
              placeholder="NAS dashboard"
            />
          </label>
          <label>
            Description <span className="optional">Optional</span>
            <textarea
              maxLength={1000}
              value={form.description ?? ''}
              onChange={(e) => set('description', e.target.value)}
              placeholder="What this service does"
            />
          </label>
          <fieldset>
            <legend>Monitor type</legend>
            <div className="segmented">
              {(['HTTP', 'TCP'] as MonitorType[]).map((type) => (
                <button
                  key={type}
                  type="button"
                  className={form.type === type ? 'active' : ''}
                  aria-pressed={form.type === type}
                  onClick={() => set('type', type)}
                >
                  {type}
                </button>
              ))}
            </div>
          </fieldset>
          <div className="field-grid">
            <label className="field-grow">
              {form.type === 'HTTP' ? 'URL' : 'Host'}
              <input
                required
                maxLength={2048}
                value={form.target}
                onChange={(e) => set('target', e.target.value)}
                placeholder={form.type === 'HTTP' ? 'https://service.lan/health' : 'server.lan'}
              />
            </label>
            {form.type === 'TCP' ? (
              <label>
                Port
                <input
                  required
                  min={1}
                  max={65535}
                  type="number"
                  value={form.port ?? ''}
                  onChange={(e) => set('port', number(e.target.value))}
                  placeholder="443"
                />
              </label>
            ) : null}
          </div>
          <div className="field-grid three">
            <label>
              Interval (sec)
              <input
                required
                min={60}
                max={86400}
                type="number"
                value={form.intervalSeconds}
                onChange={(e) => set('intervalSeconds', Number(e.target.value))}
              />
            </label>
            <label>
              Timeout (ms)
              <input
                required
                min={100}
                max={30000}
                type="number"
                value={form.timeoutMillis}
                onChange={(e) => set('timeoutMillis', Number(e.target.value))}
              />
            </label>
            <label>
              Latency warning
              <input
                min={1}
                max={30000}
                type="number"
                value={form.latencyWarningMillis ?? ''}
                onChange={(e) => set('latencyWarningMillis', number(e.target.value))}
              />
            </label>
          </div>
          <div className="field-grid three">
            <label>
              Failure threshold
              <input
                required
                min={1}
                max={100}
                type="number"
                value={form.failureThreshold}
                onChange={(e) => set('failureThreshold', Number(e.target.value))}
              />
            </label>
            <label>
              Recovery threshold
              <input
                required
                min={1}
                max={100}
                type="number"
                value={form.recoveryThreshold}
                onChange={(e) => set('recoveryThreshold', Number(e.target.value))}
              />
            </label>
            {form.type === 'HTTP' ? (
              <label>
                Expected status
                <input
                  min={100}
                  max={599}
                  type="number"
                  value={form.expectedHttpStatus ?? ''}
                  onChange={(e) => set('expectedHttpStatus', number(e.target.value))}
                />
              </label>
            ) : null}
          </div>
          <label className="toggle-row">
            <span>
              <strong>Monitoring enabled</strong>
              <small>Run checks on the configured interval</small>
            </span>
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => set('enabled', e.target.checked)}
            />
          </label>
          {error ? (
            <p className="form-error" role="alert">
              {error}
            </p>
          ) : null}
          <div className="drawer-actions">
            <button type="button" className="button secondary" onClick={onCancel}>
              Cancel
            </button>
            <button className="button primary" disabled={busy}>
              {busy ? 'Saving…' : monitor ? 'Save changes' : 'Create monitor'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}

export function ConfirmDialog({
  title,
  children,
  busy,
  error,
  confirmLabel,
  onCancel,
  onConfirm,
}: {
  title: string
  children: ReactNode
  busy: boolean
  error?: string
  confirmLabel: string
  onCancel: () => void
  onConfirm: () => void
}) {
  const dialog = useRef<HTMLElement>(null)
  useModal(onCancel, dialog, '[data-initial-focus]')
  const titleId = useId()
  return (
    <div className="drawer-backdrop">
      <section
        ref={dialog}
        className="confirm-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <h2 id={titleId}>{title}</h2>
        <p>{children}</p>
        {error ? (
          <p className="form-error" role="alert">
            {error}
          </p>
        ) : null}
        <div className="drawer-actions">
          <button className="button secondary" data-initial-focus onClick={onCancel}>
            Cancel
          </button>
          <button className="button danger" disabled={busy} onClick={onConfirm}>
            {busy ? 'Removing…' : confirmLabel}
          </button>
        </div>
      </section>
    </div>
  )
}
