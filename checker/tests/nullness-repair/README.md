# Nullness Repair Tests

This directory contains file-based end-to-end tests for trace-guided Nullness repairs.

Each `.java` file is a normal Checker Framework test input:

- use `// :: error: (...)` comments for expected Nullness diagnostics,
- add `// @repair-kind: <diagnostic-key>` to choose the diagnostic slice to repair,
- optionally add `// @repair-mode: pass|decrease|sketch`; the default is `pass`,
- optionally add `// @repair-runner: patch|search|agent`; the default is `patch`,
- add one or more `// @repair-contains: <patched source fragment>` comments for automatic repairs,
- use `// @repair-contains-raw: <patched source fragment>` when leading whitespace matters,
- optionally add `// @repair-plan-kind: <kind>` to assert a planner output kind.
- optionally add `// @repair-plan-risk: <risk>`, `// @repair-plan-automatic: true|false`, and
  `// @repair-plan-edits: <count>` to assert the selected plan shape.
- for search cases, optionally add `// @repair-search-report: true` and report assertions such as
  `// @repair-search-diagnostic-ids: E1,E2`,
  `// @repair-search-generated-candidates: <count>`, and
  `// @repair-search-searched-candidates: <count>`, and
  `// @repair-search-pruned-empty: <count>`,
  `// @repair-search-pruned-empty-reason: <reason>=<count>`, and
  `// @repair-search-pruned-duplicate: <count>`,
  `// @repair-search-pruned-overlap: <count>`, and
  `// @repair-search-pruned-budget: <count>`.
  Use `// @repair-search-event: <event>` and
  `// @repair-search-report-contains: <json fragment>` for event-level report assertions.
  Use `// @repair-max-search-candidates: <count>` to exercise search budget behavior.
- add `// @repair-include-sketch-edits: true` on search cases that should validate conservative
  non-automatic body-changing edits such as generated null checks.

The `NullnessRepairTest` JUnit runner exports a Nullness trace, invokes `checker_reconcile`, applies
automatic edits when available, and validates the patched file. The `agent` runner exercises the
agent-context -> proposal -> agent-plan -> validation-report -> final-context loop with a
deterministic in-harness proposal.
