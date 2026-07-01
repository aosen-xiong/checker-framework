# Checker Framework Nullness Integration Notes

Public checker invocation:

- `checker/src/main/java/org/checkerframework/checker/nullness/NullnessChecker.java`
- Class: `org.checkerframework.checker.nullness.NullnessChecker`
- Used with javac as:
  `-processor org.checkerframework.checker.nullness.NullnessChecker`
- `NullnessChecker#getTargetCheckerClass()` returns `NullnessNoInitSubchecker.class`.

Option parsing:

- `framework/src/main/java/org/checkerframework/framework/source/SourceChecker.java`
- `SourceChecker#getSupportedOptions()` collects `@SupportedOptions` from checker classes.
- `SourceChecker#getOptions()`, `hasOption(String)`, and `getOption(String)` expose active `-A`
  options.
- This prototype registers `exportNullnessTrace` on `NullnessChecker` and
  `NullnessNoInitSubchecker`.

Nullness visitor:

- `checker/src/main/java/org/checkerframework/checker/nullness/NullnessNoInitSubchecker.java`
- `NullnessNoInitSubchecker#createSourceVisitor()` returns `new NullnessNoInitVisitor(this)`.
- `checker/src/main/java/org/checkerframework/checker/nullness/NullnessNoInitVisitor.java`
- `NullnessNoInitVisitor#commonAssignmentCheck(Tree, ExpressionTree, String, Object...)`
  handles the Nullness-specific `@MonotonicNonNull` initialization exception before delegating to
  `BaseTypeVisitor`.

Assignment, return, argument, and receiver checks:

- `framework/src/main/java/org/checkerframework/common/basetype/BaseTypeVisitor.java`
- `visitAssignment(AssignmentTree, Void)` calls
  `commonAssignmentCheck(tree.getVariable(), tree.getExpression(), "assignment.type.incompatible")`.
- `visitReturn(ReturnTree, Void)` computes the enclosing method/lambda return type and calls
  `commonAssignmentCheck(declaredReturnType, tree.getExpression(), "return.type.incompatible")`.
- `visitMethodInvocation(MethodInvocationTree, Void)` obtains the invoked executable type and calls
  `checkArguments(...)` and `checkMethodInvocability(...)`.
- `checkArguments(...)` calls
  `commonAssignmentCheck(requiredType, passedArg, "argument.type.incompatible", paramName,
  executableName)`.
- `checkMethodInvocability(AnnotatedExecutableType, MethodInvocationTree)` checks the receiver with
  `typeHierarchy.isSubtype(erasedTreeReceiver, erasedMethodReceiver)` and reports
  `method.invocation.invalid`.

Subtype checks:

- `framework/src/main/java/org/checkerframework/framework/type/TypeHierarchy.java`
- Interface method: `isSubtype(AnnotatedTypeMirror subtype, AnnotatedTypeMirror supertype)`.
- `framework/src/main/java/org/checkerframework/framework/type/DefaultTypeHierarchy.java`
- Main implementation entry: `DefaultTypeHierarchy#isSubtype(AnnotatedTypeMirror,
  AnnotatedTypeMirror)`.
- `BaseTypeVisitor#commonAssignmentCheck(AnnotatedTypeMirror, AnnotatedTypeMirror, Tree, String,
  Object...)` widens the actual type with `atypeFactory.getWidenedType(...)`, calls
  `typeHierarchy.isSubtype(widenedValueType, varType)`, and reports via
  `reportCommonAssignmentError(...)` on failure.

Annotated types and display:

- `framework/src/main/java/org/checkerframework/framework/type/AnnotatedTypeMirror.java`
- Annotated types are represented by `AnnotatedTypeMirror` and nested subclasses such as
  `AnnotatedDeclaredType`, `AnnotatedArrayType`, and `AnnotatedExecutableType`.
- Existing display path used by diagnostics is `AnnotatedTypeMirror#toString()`, often after
  `BaseTypeVisitor.FoundRequired.of(...)`.

Source positions and trees:

- `com.sun.source.tree.Tree` represents source AST nodes.
- `com.sun.source.util.TreePath` is available from `SourceVisitor#getCurrentPath()`.
- `framework/src/main/java/org/checkerframework/framework/source/SourceVisitor.java` stores the
  current `CompilationUnitTree root`.
- `BaseTypeVisitor` stores `SourcePositions positions = trees.getSourcePositions()`.
- Source ranges use `positions.getStartPosition(root, tree)`,
  `positions.getEndPosition(root, tree)`, and `root.getLineMap()`.
- Element symbols are `javax.lang.model.element.Element`; common helpers are in
  `org.checkerframework.javacutil.TreeUtils`, including `TreeUtils.elementFromUse(...)` and
  `TreeUtils.getReceiverTree(...)`.

Dataflow refinements:

- `checker/src/main/java/org/checkerframework/checker/nullness/NullnessNoInitAnnotatedTypeFactory.java`
  is the Nullness annotated type factory.
- Flow-sensitive facts are represented through the Checker Framework dataflow layer imported by
  `BaseTypeVisitor`, including `CFAbstractStore`, `CFAbstractValue`, `TransferResult`, and
  `JavaExpression`.
- V0 reserves `flow_refinement` events but does not yet export them.

Diagnostics:

- `framework/src/main/java/org/checkerframework/framework/source/SourceChecker.java`
- `SourceChecker#reportError(Object, String, Object...)` delegates to private `report(...)`.
- `report(...)` suppresses diagnostics as needed, formats messages using checker message bundles,
  and emits them through `Messager` or `printOrStoreMessage(...)`.
- The V0 trace exporter emits diagnostic trace events adjacent to the failed subtype decision, then
  leaves normal Checker Framework diagnostic emission unchanged.

V0 trace hook points:

- `framework/src/main/java/org/checkerframework/common/basetype/NullnessTraceSink.java`
- `BaseTypeVisitor#commonAssignmentCheck(AnnotatedTypeMirror, AnnotatedTypeMirror, Tree, String,
  Object...)` emits assignment, field assignment, method argument, and return obligations.
- `BaseTypeVisitor#checkMethodInvocability(...)` emits receiver non-nullness obligations.
- `NullnessNoInitVisitor#checkForNullability(...)` emits nullable receiver dereference obligations
  for `dereference.of.nullable` via `BaseTypeVisitor#traceNullnessDereference(...)`.
- The same `checkForNullability(...)` path emits generic non-null context obligations for
  `condition.nullable`, `unboxing.of.nullable`, `accessing.nullable`, and
  `iterating.over.nullable` via `BaseTypeVisitor#traceNullnessNonNullCheck(...)`.

V0 external repair/validation:

- `checker_reconcile/src/main/java/checker_reconcile/Cli.java`
- `repair --source Example.java --trace trace.jsonl --out Patched.java` writes a conservative patch.
- `repair ... --validate --javac javac --checker checker/dist/checker.jar` validates the patched
  file with `org.checkerframework.checker.nullness.NullnessChecker`.
- `checker_reconcile/src/main/java/checker_reconcile/repair/RepairPlanner.java` converts one
  diagnostic slice into typed `SuggestedRepair` plans.
- `checker_reconcile/src/main/java/checker_reconcile/repair/ExplicitQualifierWeakeningRule.java`
  contains the V0 automatic nullness rule: explicit mapped `@NonNull` to `@Nullable` for local,
  field, parameter, and return annotations.
- `checker_reconcile/src/main/java/checker_reconcile/repair/PatchApplier.java` applies
  `SourceEdit` ranges and does not know Checker Framework, slot, qualifier, or diagnostic
  semantics.
- `checker_reconcile/src/main/java/checker_reconcile/repair/AgentRepairAdvisor.java` is the
  dependency-free extension point for larger AI-assisted repair or refactor suggestions. The default
  advisor is `NoopAgentRepairAdvisor`, and agent suggestions are non-automatic until lowered to
  edits and validated.
- `checker_reconcile.Cli plan --source Example.java --trace trace.jsonl` emits schema-versioned
  JSONL `SuggestedRepair` plans for machine consumers.
- `checker_reconcile.Cli apply-plan --source Example.java --plan plan.jsonl --out Patched.java`
  applies automatic edit-backed JSONL plans. Add `--validate --javac javac --checker checker.jar`
  to recompile the patched source with the Nullness Checker. Validation defaults to
  `--validation-mode pass`; `--validation-mode decrease` accepts strictly lower diagnostic counts.
  Add `--dry-run` to print selected automatic edit-backed plans without writing output.
- `checker_reconcile/src/main/java/checker_reconcile/repair/Patcher.java` is a compatibility facade
  for existing CLI and test-harness callers.
- `checker/src/test/java/org/checkerframework/checker/test/junit/NullnessTraceExportTest.java`
  includes an end-to-end CF-harness test: compile failing Nullness input with trace export, patch
  from the trace, then recompile the patched file with the Nullness Checker.

V0 CF test-suite mining:

- `checker_reconcile.Cli mine-cf-tests --root checker/tests/nullness --examples
  --repair-candidates --limit 20` summarizes real Checker Framework Nullness test diagnostics by
  kind and prints bounded examples.
- This is intended to select representative assignment, argument, return, and receiver examples
  before broadening repair rules.
- The `--repair-candidates` bucket is intentionally aligned with the V0 patcher: assignment
  diagnostics whose following code line starts with an explicit simple `@NonNull` declaration.

Corpus reporting:

- `checker_reconcile.Cli corpus-report --root checker/tests/nullness --out
  build/reports/nullness-repair-corpus/report.jsonl --summary
  build/reports/nullness-repair-corpus/summary.txt --allow-risk LOCAL_ONLY,API_CHANGE
  --validation-mode decrease --limit 100 --javac javac --checker checker.jar` copies each mined
  source into a work directory, exports a trace with Checker Framework, runs diagnostic-filtered
  validation-backed repair search, and emits one JSONL attempt per diagnostic plus a summary.
- Corpus defaults are `LOCAL_ONLY,API_CHANGE`, `decrease`, `maxCandidateSize=3`,
  `maxSearchCandidates=100`, and no sketch edits. Each attempt also records whether the patched
  file fully passes.
- Original Checker Framework test files are not patched or compiled in place by the corpus runner.
