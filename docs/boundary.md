# Checker Reconcile Boundary

Checker Framework remains the trusted Nullness Checker. The Checker Framework code in this
prototype only emits JSONL trace events when `-AexportNullnessTrace=PATH` is passed.

The external `checker_reconcile` Java package parses traces, slices diagnostics, computes V0
MUS/MCS-style explanations over trace IDs, produces typed repair plans, lowers automatic plans to
source edits, applies those edits textually, and invokes javac for validation.

V0 automatic repair is intentionally narrow: it may change an explicit mapped `@NonNull`
annotation to `@Nullable` when the trace identifies an editable local, field, parameter, or return
assumption and an exact annotation range. Local changes are `LOCAL_ONLY`; field, parameter, and
return changes are `API_CHANGE` and should be selected explicitly by risk. It does not add
suppressions, delete code, or change method bodies.

Receiver dereference failures such as `dereference.of.nullable` are traced as non-editable
`receiver_nonnull` obligations. In V0 they are sketch-only `BODY_CHANGE` repairs, because a real fix
usually requires inserting a null check, changing control flow, or refactoring contracts.
Other nullable-expression context failures such as `condition.nullable`, `unboxing.of.nullable`,
`accessing.nullable`, and `iterating.over.nullable` are traced as non-editable `nonnull`
obligations and are also sketch-only in V0.

The repair boundary is:

```text
TraceEvent
  -> TraceModel.DiagnosticSlice
  -> ConstraintGraph
  -> RepairCandidateSet[]
  -> SourceTarget
  -> SuggestedRepair[]
  -> optional AgentRepairAdvisor
  -> SourceEdit[]
  -> PatchApplier
  -> CF validation
```

`RepairPlanner` and its rules own checker/nullness semantics. `SourceTargetResolver` maps editable
trace assumptions to syntactic targets. `PatchApplier` only applies validated character-offset edits
and must not inspect slots, qualifiers, diagnostic keys, or Checker Framework-specific trace fields.
Each deterministic repair is a `RepairRule`; rules may emit automatic edit-backed plans or
non-automatic sketches.

`DiagnosisEngine` builds a typed `ConstraintGraph` from trace assumptions, obligations,
diagnostics, slots, and qualifiers. Its default `BoundedDiagnosisSolver` is deliberately
dependency-free: it ranks bounded repair candidate sets by assumption weight, repair risk,
automatic/edit-backed status, and set size. This is the solver abstraction boundary; a future
MaxSAT/ILP backend should implement `DiagnosisSolver` without changing repair rules or patching.

Agent-assisted repair is an optional planner extension, not part of Checker Framework. An
`AgentRepairAdvisor` receives a diagnostic slice plus deterministic repair plans and may append
non-automatic `SuggestedRepair` sketches for larger body-changing repairs or refactors. Those
repairs still have to lower to `SourceEdit`s and pass Checker Framework validation before they are
accepted.

Machine consumers should use the JSONL planning surface:

```sh
java checker_reconcile.Cli plan --source Example.java --trace trace.jsonl
```

Each output line is one schema-versioned `SuggestedRepair` JSON object with diagnostic id, kind,
risk, automatic flag, message, evidence ids, and concrete edits when available. This is the intended
integration point for an external AI agent or workflow engine.

Plans can be applied and validated through the same CLI boundary:

```sh
java checker_reconcile.Cli apply-plan \
  --source Example.java \
  --plan plan.jsonl \
  --out Patched.java \
  --validate \
  --javac javac \
  --checker checker.jar
```

`apply-plan` applies only automatic edit-backed plans. Validation remains a Checker Framework
recompile gate. `--validation-mode pass` is the default and requires a clean Nullness Checker
compile. `--validation-mode decrease` accepts a patch only when the post-patch diagnostic count is
strictly lower than the pre-patch count. Use `--diagnostic`, `--kind`, and `--allow-risk` to select
a subset of a multi-plan JSONL file before applying. Use `--dry-run` to print selected automatic
edit-backed plans without writing the patched file or validating. Selected automatic edits are
preflighted as a batch; if any edit has an invalid range, targets another file, or overlaps another
selected edit, no patched file is written.

Validation-backed search uses the same ranked candidates but validates each automatic edit-backed
candidate before accepting it:

```sh
java checker_reconcile.Cli search-repair \
  --source Example.java \
  --trace trace.jsonl \
  --out Patched.java \
  --validation-mode decrease \
  --allow-risk LOCAL_ONLY \
  --javac javac \
  --checker checker.jar
```

`search-repair` tries ranked `RepairCandidateSet`s in order, materializes each candidate in a
temporary source file with the same filename as the original, runs Checker Framework validation, and
copies the first accepted candidate to `--out`. `pass` requires a clean checker run; `decrease`
requires a strictly lower diagnostic count.

Do not add LangChain, LangGraph, or a vendor SDK dependency to the core Java repair layer. If an AI
orchestration runtime is useful later, adapt it outside this package to the small
`AgentRepairAdvisor` interface.

The external layer may mine Checker Framework test suites for examples, but it still treats Checker
Framework as the oracle by recompiling candidate patches.

Do not implement MUS, MCS, MaxSAT, repair planning, or prompting inside Checker Framework.
Do not make the external layer a replacement Nullness Checker.
