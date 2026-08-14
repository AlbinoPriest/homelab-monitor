# ADR-0004: Persist observation validity for availability metrics

## Status

Accepted

## Context

HomeLab Monitor can be stopped, delayed, or disconnected from a target. A monitor's last operational status may
remain known while there is no fresh evidence for later time. Counting checks or extending the last state until
the next result would silently classify monitoring gaps as uptime or downtime and make historical results change
when a monitor's interval or timeout is edited.

Threshold progress has the same continuity problem: failures collected on opposite sides of an unobserved gap
must not combine into a confirmed outage, and separated recovery successes must not resolve one.

## Decision

Persist an `observation_valid_until` boundary with every accepted check and on the monitor's current observation.
Calculate it from the interval, timeout, and scanner tolerance that applied when the check ran. Duration analytics
intersect state-history intervals with the union of these persisted observation windows. Time outside that union,
plus `UNKNOWN` and `PAUSED` state, is reported as excluded rather than available or unavailable.

When current evidence expires, record the transition at the computed boundary and clear incomplete failure or
recovery progress. Keep a confirmed `OFFLINE` state and its active incident until fresh threshold recovery or a
deliberate pause, while still excluding unobserved time from availability metrics.

## Alternatives considered

- Check-count uptime is simple but weights frequent checks more heavily and cannot represent duration or gaps.
- Extending each state until the next check fabricates continuity across process downtime and delayed execution.
- Recomputing old validity from the monitor's current configuration lets later edits rewrite historical meaning.
- Expiring confirmed offline state to unknown would hide an unresolved operational fact and bypass recovery
  thresholds.

## Consequences

Metrics distinguish known uptime, known downtime, and excluded time honestly, and historical windows keep their
original meaning after configuration changes. Freshness scanning and result completion must coordinate on the
same per-monitor execution gate and database lock so expiry and completion remain ordered. The data model and
queries are more involved than check-count reporting, and retention must remove raw checks only after their
observation coverage ends.
