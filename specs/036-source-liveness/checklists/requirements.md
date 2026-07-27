# Specification Quality Checklist: Event-Source Liveness Heartbeat

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-07-26
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Notes

- Directly completes 035's explicit gap: the Source screen showed config + cursors but could not say whether
  the poller was *running*. A heartbeat (last-poll time + cycle count) makes "is it alive?" answerable.
- **No false alarms**: a missing heartbeat reads as "unknown", not "stale"; the heartbeat refreshes only on a
  successful poll, so a rate-limited poller correctly trends stale.
- Mirrors 035's shape — a small file the CLI writes and the server reads — so there is no process coupling, and
  it stays observability-only (no start/stop from the UI).
