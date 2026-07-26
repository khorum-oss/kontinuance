# Specification Quality Checklist: Cancel a Running Run from the UI

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-07-25
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

- The load-bearing insight is that **the engine already cancels correctly** (terminates the step, ends
  `Cancelled`, no orphaned process) — the only missing piece was addressing the run by the server's id. A
  small optional `runId` on `engine.run` (defaulting to a generated id) closes that gap additively, so no
  existing caller changes.
- **Cancel targets actively-running runs.** Waiting-at-a-gate is ended via reject (the run isn't executing);
  terminal runs are done. Both are reported "not running" (409) rather than silently no-op'd, so the control
  and the API stay honest.
