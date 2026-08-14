# ADR-0002: Use authenticated non-replayed SSE

## Status

Accepted

## Context

The owner dashboard needs prompt server-to-browser refreshes for completed checks, status changes, incidents,
freshness, and monitor configuration. Version 1 is a single-instance deployment with one owner and roughly 100
monitors. The backend remains authoritative, and a realtime transport must not create a second state store or let
slow browsers interfere with monitoring work.

## Decision

Expose an authenticated Server-Sent Events stream at `/api/v1/events`. Publish compact change-cause payloads only
after their database transaction commits. Keep no replay log: the React client treats events as cache-invalidation
hints and refetches all mounted authoritative queries whenever EventSource initially connects or reconnects.

Bound streams, connection lifetime, pending events, heartbeats, and delivery threads. Give each subscription one
delivery lane so a stalled client cannot consume other clients' lanes or monitoring threads. Associate streams with
their server session and close them on logout or session destruction.

## Alternatives considered

- WebSockets add bidirectional protocol and lifecycle complexity without a client-to-server realtime requirement.
- Polling alone is simpler but either delays important changes or creates continuous broad API load.
- Durable SSE replay would close reconnect gaps precisely but adds event persistence, retention, identifiers, and
  consistency work that is unnecessary when bounded refetches can recover current state.

## Consequences

Delivery is simple, same-origin, and compatible with server-side sessions. Short disconnects may miss individual
notifications, but reconnect refetches recover current truth. Events are not an audit log, and production proxying
must preserve streaming and disable response buffering. The in-memory broker remains intentionally unsuitable for
multiple backend replicas; that is outside version 1's single-instance scope.
