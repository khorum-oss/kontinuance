# Contract: Descriptor Validity (source of truth for the example)

`docs/examples/kontinuance.yml` MUST satisfy these rules, taken from
`engine/src/main/kotlin/org/khorum/oss/kontinuance/engine/descriptor/PipelineDescriptor.kt` (strict:
unknown or duplicate keys are rejected). This is the check behind FR-005 and SC-003.

## Shape

- Top level: exactly one key, `pipeline:`.
- `pipeline` keys ⊆ `{ name, concurrency, stages }`; `name` required.
- `stage` keys ⊆ `{ name, steps }`; `name` required.
- `step` keys ⊆ `{ name, timeout, when, secrets, workingDir }` **plus exactly one** definition key from
  `{ run, gradle, docker, npm, approval }`.
- `when:` is the condition key (maps to the model's `condition`); **not** `condition:`.
- `secrets:` is a list of secret names (each resolved from the environment).
- `approval:` value is the human-readable prompt string shown to the approver.

## Gate placement (durable-approval authoring rule)

- The manual-approval gate MUST be the sole step of its own stage, placed **after** build/test and
  **before** deploy. On resume the paused stage is re-entered from the top, so a gate alone in its stage
  means resume repeats no prior work.

## Verification

The example is loaded through the real parser (`PipelineDescriptor.parse`) — see `quickstart.md`. A pass
means the file is accepted exactly as the server would accept it as its configured descriptor.
