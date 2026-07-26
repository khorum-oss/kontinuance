# Specification Quality Checklist: Arbitrary Commit-SHA Checkout

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

- Closes the caveat both 015 (source-checkout) and 033 (per-project source) called out: the checkout could
  only name a branch/tag, never an exact commit. A pinned SHA gives reproducible, drift-free builds.
- **Additive & safe**: a new `sha` key on the `git:` step (mutually exclusive with `ref`), realized in the
  executor as a fetch-then-checkout with url/sha/dir passed as positional shell parameters (no interpolation).
  A checkout without a SHA is the unchanged clone path.
- The descriptor keeps `ref`/`sha` as **separate keys** (no guessing); the hex heuristic applies only to the
  project source's single UI field.
