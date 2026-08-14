import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from './api'

type RealtimeEvent = {
  monitorId: string
  changes: string[]
}

function isRealtimeEvent(value: unknown): value is RealtimeEvent {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as { monitorId?: unknown }).monitorId === 'string' &&
    Array.isArray((value as { changes?: unknown }).changes) &&
    (value as { changes: unknown[] }).changes.every((change) => typeof change === 'string')
  )
}

export function useRealtime() {
  const client = useQueryClient()

  useEffect(() => {
    if (typeof EventSource === 'undefined') return

    const pendingChanges = new Map<string, Set<string>>()
    let refreshTimer: ReturnType<typeof setTimeout> | undefined
    const source = new EventSource('/api/v1/events', { withCredentials: true })

    const refresh = (event: RealtimeEvent) => {
      const changes = pendingChanges.get(event.monitorId) ?? new Set<string>()
      event.changes.forEach((change) => changes.add(change))
      pendingChanges.set(event.monitorId, changes)
      if (refreshTimer) return
      refreshTimer = setTimeout(() => {
        refreshTimer = undefined
        void client.invalidateQueries({ queryKey: ['monitors'] })
        void client.invalidateQueries({ queryKey: ['analytics'] })
        const incidentChanged = [...pendingChanges.values()].some(
          (value) =>
            value.has('INCIDENT_OPENED') ||
            value.has('INCIDENT_RESOLVED') ||
            value.has('MONITOR_DELETED'),
        )
        if (incidentChanged) void client.invalidateQueries({ queryKey: ['incidents'] })
        for (const [id, changes] of pendingChanges) {
          void client.invalidateQueries({ queryKey: ['monitor', id] })
          if (changes.has('CHECK_COMPLETED')) {
            void client.invalidateQueries({ queryKey: ['checks', id] })
          }
          if (
            changes.has('STATUS_CHANGED') ||
            changes.has('MONITOR_CREATED') ||
            changes.has('MONITOR_DELETED')
          ) {
            void client.invalidateQueries({ queryKey: ['history', id] })
          }
          void client.invalidateQueries({ queryKey: ['metrics', id] })
        }
        pendingChanges.clear()
      }, 100)
    }

    source.onopen = () => {
      // The server deliberately stores no replay log, so every (re)connect
      // resynchronizes all currently mounted authoritative queries.
      void client.invalidateQueries({ queryKey: ['monitors'] })
      void client.invalidateQueries({ queryKey: ['incidents'] })
      void client.invalidateQueries({ queryKey: ['analytics'] })
      void client.invalidateQueries({ queryKey: ['monitor'] })
      void client.invalidateQueries({ queryKey: ['checks'] })
      void client.invalidateQueries({ queryKey: ['history'] })
      void client.invalidateQueries({ queryKey: ['metrics'] })
    }
    source.onmessage = (message) => {
      try {
        const event: unknown = JSON.parse(message.data)
        if (isRealtimeEvent(event)) refresh(event)
      } catch {
        // Ignore malformed server events; the next reconnect still performs a full sync.
      }
    }
    source.onerror = () => {
      // EventSource reconnects automatically. Confirm auth separately because it
      // does not expose an HTTP status when a session expires.
      void api
        .authStatus()
        .then((status) => {
          if (!status.authenticated) client.setQueryData(['auth'], status)
        })
        .catch(() => undefined)
    }

    return () => {
      if (refreshTimer) clearTimeout(refreshTimer)
      source.close()
    }
  }, [client])
}
