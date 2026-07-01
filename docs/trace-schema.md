# Nullness Trace Schema V0

Each line is one JSON object.

Known events:

- `assumption`
- `obligation`
- `diagnostic`
- `flow_refinement` reserved
- `inference_decision` reserved

`assumption` fields:

- `event`, `id`, `kind`, `slot`, `type`, `editable`, `weight`
- optional `file`, `range`, `source_target`
- known `kind` values include `actual_qualifier`, `target_qualifier`,
  `receiver_qualifier`, and `receiver_contract`

`obligation` fields:

- `event`, `id`, `kind`, `relation`, `got`, `want`, `slots`, `dependencies`, `result`
- optional `diagnostic_id`, `file`, `range`, `actual_range`, `expected_range`
- known `kind` values include `assignment`, `field_assignment`, `method_argument`, `return`, and
  `dereference`, `condition`, `unboxing`, `array_access`, and `iteration`
- `dereference` obligations use `relation: receiver_nonnull`; assignment-like obligations use
  `relation: subtype`; other non-null context checks use `relation: nonnull`

`diagnostic` fields:

- `event`, `id`, `error_kind`, `message`, `obligation`
- optional `file`, `range`

Ranges use zero-based javac character offsets plus one-based javac line/column numbers:

- `start_offset`
- `start_line`
- `start_col`
- `end_offset`
- `end_line`
- `end_col`

Assumptions may include a richer `source_target` when the exporter can identify a precise source
target. Editable qualifier assumptions use annotation targets, which the patch backend may edit
directly:

```json
{
  "kind": "local_annotation",
  "annotation": "@NonNull",
  "annotation_range": {
    "start_offset": 120,
    "end_offset": 128,
    "start_line": 4,
    "start_col": 5,
    "end_line": 4,
    "end_col": 13
  }
}
```

Known annotation `source_target.kind` values are:

- `local_annotation`
- `field_annotation`
- `parameter_annotation`
- `return_annotation`

The external repair planner requires `source_target.annotation_range` before emitting an automatic
qualifier-changing edit. If no explicit annotation tree is mapped, the planner may still emit
non-automatic sketches.

Non-editable receiver and nullable-expression assumptions use expression targets, which are
evidence for body-changing repairs and agent proposals:

```json
{
  "kind": "receiver_expression",
  "expression": "s",
  "expression_range": {
    "start_offset": 145,
    "end_offset": 146,
    "start_line": 5,
    "start_col": 5,
    "end_line": 5,
    "end_col": 6
  }
}
```

Known expression `source_target.kind` values are:

- `receiver_expression`
- `condition_expression`
- `unboxing_expression`
- `array_expression`
- `iteration_expression`

Expression targets are not considered automatic patch targets in V0.

Receiver dereference diagnostics are represented as non-editable receiver obligations:

```json
{
  "event": "obligation",
  "kind": "dereference",
  "relation": "receiver_nonnull",
  "slots": {
    "actual": "receiver:s",
    "expected": "method-contract:nonnull-receiver"
  },
  "result": "error"
}
```

The corresponding diagnostic uses `error_kind: "dereference.of.nullable"`. V0 treats these as
body-changing repairs and emits non-automatic sketches such as `ADD_NULL_CHECK`.

Other nullable-expression context diagnostics are represented as non-editable non-null obligations:

```json
{
  "event": "obligation",
  "kind": "condition",
  "relation": "nonnull",
  "slots": {
    "actual": "condition:b",
    "expected": "nonnull-contract:condition"
  },
  "result": "error"
}
```

Known V0 context kinds are:

- `condition` for `condition.nullable`
- `unboxing` for `unboxing.of.nullable`
- `array_access` for `accessing.nullable`
- `iteration` for `iterating.over.nullable`

V0 also treats these as body-changing repairs and emits non-automatic sketches.

# Repair Plan Schema V1

`checker_reconcile.Cli plan` and `apply-plan --dry-run` emit one JSON object per line with
`"schema_version": 1`.

Required fields:

- `schema_version`: numeric `1`
- `diagnostic_id`
- `kind`
- `risk`
- `automatic`
- `message`
- `evidence_ids`
- `edits`

Optional provenance and validation fields:

- `origin`: non-empty string, defaults to `deterministic`; external agents should use `agent`
- `confidence`: numeric score from `0.0` to `1.0`
- `requires_validation`: boolean, defaults to `false`
- `sketches`: structured non-applied repair targets for body-changing or otherwise non-automatic
  repairs

Each edit must contain numeric `start_offset` and `end_offset` fields plus a `replacement` string.
`apply-plan` rejects malformed schema versions, missing/invalid `kind` or `risk`, invalid optional
metadata, and non-numeric edit offsets before applying edits. Automatic plans with `origin:
agent` or `requires_validation: true` are rejected unless `apply-plan --validate` is used.

`sketches` are not applied by the V1 patch backend. They describe a future or agent-assisted repair
target:

```json
{
  "kind": "add_null_check",
  "target_id": "A1",
  "automatic": false,
  "message": "Insert a null check guarding s.",
  "source_target": {
    "kind": "receiver_expression",
    "expression": "s",
    "expression_range": {
      "start_offset": 145,
      "end_offset": 146
    }
  }
}
```

`checker_reconcile.Cli plan --include-sketch-edits` can additionally materialize conservative
non-automatic edits for simple `ADD_NULL_CHECK` sketches. These edits remain `automatic: false`;
the default repair path still ignores them, but agents and reviewers can inspect or validate them.
`checker_reconcile.Cli search-repair --include-sketch-edits --allow-risk BODY_CHANGE` can include
the same edits in validation-backed search; acceptance still requires the patched source to satisfy
the selected validation mode.

`checker_reconcile.Cli lint-plan --source Example.java --plan plan.jsonl` validates the same schema
without applying edits and prints a preflight summary by origin, kind, risk, automatic flag, edit
count, validation-required count, and agent automatic edit count. This is intended for external
agent loops that need a cheap check before invoking `apply-plan --validate`.

# Validation Report Schema V1

`checker_reconcile.Cli apply-plan --validate --validation-report validation.jsonl` writes one JSON
object with `"schema_version": 1` after validation, before exiting on rejection.

Required fields:

- `schema_version`: numeric `1`
- `event`: `validation_result`
- `source`
- `patched_source`
- `validation_mode`
- `accepted`
- `applied_plan_count`
- `applied_edit_plan_count`
- `diagnostic_ids`
- `agent_origin_count`
- `validation_required_count`

For `validation_mode: pass`, the report includes:

- `after_exit_code`
- `after_diagnostic_count`

For `validation_mode: decrease`, the report includes:

- `before_exit_code`
- `before_diagnostic_count`
- `after_exit_code`
- `after_diagnostic_count`

# Agent Proposal Schema V1

`checker_reconcile.Cli agent-plan --context context.jsonl --proposal proposal.jsonl` validates
external agent proposals against an agent-context bundle and converts them to Repair Plan Schema V1
JSONL. `--source Example.java` is also accepted for schema-only proposal conversion, but
`--context` is the preferred mode for trusted agent workflows.

Required fields:

- `schema_version`: numeric `1`
- `event`: `agent_proposal`
- `diagnostic_id`
- `kind`
- `risk`
- `automatic`
- `message`
- `evidence_ids`
- `edits`

Optional fields:

- `confidence`: numeric score from `0.0` to `1.0`
- `requires_validation`: boolean, defaults to `false`

Each edit has the same shape as a repair-plan edit. Converted plans always use `origin: agent`.
Patchable automatic proposals are converted with `requires_validation: true` even if the proposal
omits it or sets it to `false`.

When `--context` is provided, `agent-plan` also checks:

- proposal `diagnostic_id` matches the context diagnostic
- every `evidence_ids` entry is present in the context diagnostic, obligation, assumptions,
  deterministic repairs, or embedded search report
- edit ranges are within the context source file length

Recommended external-agent flow:

1. `agent-context --source Example.java --trace trace.jsonl > context.jsonl`
2. external agent reads `context.jsonl` and writes `proposal.jsonl`
3. `agent-plan --context context.jsonl --proposal proposal.jsonl > plan.jsonl`
4. `lint-plan --source Example.java --plan plan.jsonl`
5. `apply-plan --source Example.java --plan plan.jsonl --out Patched.java --validate
   --validation-report validation.jsonl ...`

# Search Report Schema V1

`checker_reconcile.Cli search-repair --search-report search.jsonl` emits one JSON object per line
with `"schema_version": 1`.

Search candidate generation is bounded by `--max-candidate-size` and `--max-search-candidates`.
Before validation, duplicate edit sets and overlapping edit sets are pruned.

Known event values:

- `candidate_validated`
- `candidate_pruned`
- `candidate_skipped`
- `candidate_invalid`
- `summary`

Candidate events include:

- `schema_version`: numeric `1`
- `event`
- `candidate_index`
- `candidate_cost`
- `candidate_size`
- `diagnostic_ids`
- `repairs`

`candidate_validated` additionally includes:

- `accepted`
- `after_diagnostic_count`
- `after_exit_code`

`candidate_pruned`, `candidate_skipped`, and `candidate_invalid` additionally include:

- `reason`

Each repair entry includes:

- `kind`
- `risk`
- `automatic`
- `cost`
- `diagnostic_ids`
- `assumption_ids`
- `evidence_ids`
- `edits`
- `sketches`

Each edit has the same shape as repair-plan edits:

- `file`
- `start_offset`
- `end_offset`
- `replacement`

Each sketch has the same shape as repair-plan sketches, including optional
`materialization_failure`. `candidate_pruned` is used for candidate sets that are generated but
removed before validation, such as sketch-only repairs whose conservative edit materializer could
not produce a source edit.

The final `summary` event includes:

- `accepted`
- `before_diagnostic_count`
- `before_exit_code`
- `after_diagnostic_count`
- `after_exit_code`
- `max_candidate_size`
- `max_search_candidates`
- `generated_candidate_count`
- `searched_candidate_count`
- `pruned_empty_edit_count`
- `pruned_empty_edit_reasons`
- `pruned_duplicate_edit_count`
- `pruned_overlap_count`
- `pruned_budget_count`
- `all_diagnostic_ids`
- `validated_diagnostic_ids`
- `accepted_diagnostic_ids`
- `rejected_diagnostic_ids`
- `skipped_diagnostic_ids`
- `uncovered_diagnostic_ids`
- optional `accepted_candidate_cost`
- optional `accepted_candidate_size`

`SearchReportParser` rejects malformed schema versions, unknown event values, missing candidate
metrics, missing validation/search-space counts, missing summary diagnostic coverage arrays, and
candidate events without non-empty `diagnostic_ids` and `repairs` arrays.

# Agent Context Schema V1

`checker_reconcile.Cli agent-context --source Example.java --trace trace.jsonl` emits one JSON
object with `"schema_version": 1`.

Required top-level fields:

- `schema_version`: numeric `1`
- `event`: `agent_context`
- `source`
- `diagnostic_id`
- `diagnostic`
- `obligation`
- `assumptions`
- `deterministic_repairs`
- `search_summary`
- `search_report`
- `validation_result`

`diagnostic`, `obligation`, and each `assumptions` entry preserve the corresponding trace event
object. `deterministic_repairs` uses the same repair shape as search-report repair entries:

- `kind`
- `risk`
- `automatic`
- `message`
- `evidence_ids`
- `edits`

`search_summary` is `{}` unless `--search-report search.jsonl` is passed and the report contains a
summary event. When present, it mirrors that Search Report Schema V1 `summary` object, including
the diagnostic coverage arrays:

- `all_diagnostic_ids`
- `validated_diagnostic_ids`
- `accepted_diagnostic_ids`
- `rejected_diagnostic_ids`
- `skipped_diagnostic_ids`
- `uncovered_diagnostic_ids`

`search_report` is empty unless `--search-report search.jsonl` is passed. When present, it contains
the validated raw Search Report Schema V1 events.

`validation_result` is `{}` unless `--validation-report validation.jsonl` is passed. When present,
it contains the validated Validation Report Schema V1 object, and its `diagnostic_ids` must include
the context-level `diagnostic_id`.

This bundle is the intended boundary for external AI repair agents: the Java core exports
diagnosis, deterministic repair plans, and validation/search evidence; external agents may use that
context to propose non-automatic refactor or body-changing repairs.

`AgentContextParser` rejects malformed schema versions, unknown top-level event values, missing
top-level diagnostic/obligation/search-summary objects, malformed deterministic repair entries,
malformed populated search summaries, and embedded search-report events without
`schema_version: 1`. Populated validation results are validated as Validation Report Schema V1 and
must refer to the context diagnostic.

`checker_reconcile.Cli agent-repair --context context.jsonl` validates one agent-context bundle and
emits the bundle's `deterministic_repairs` as Repair Plan Schema V1 JSONL. Each emitted plan uses
the context-level `diagnostic_id`. This command is the Java-core fallback path for agent workflows:
external agents can consume the same context format, propose their own plans out of process, and
still hand plans back to `apply-plan`.

Supported filters:

- `--kind CHANGE_QUALIFIER`
- `--allow-risk LOCAL_ONLY`
- `--automatic-only`
