package checker_reconcile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import checker_reconcile.constraints.Nullness;
import checker_reconcile.constraints.TraceModel;
import checker_reconcile.corpus.CfTestMetadata;
import checker_reconcile.corpus.CorpusAttempt;
import checker_reconcile.corpus.CorpusCase;
import checker_reconcile.corpus.CorpusEdit;
import checker_reconcile.corpus.CorpusReportJson;
import checker_reconcile.corpus.CorpusSummary;
import checker_reconcile.diagnosis.BoundedDiagnosisSolver;
import checker_reconcile.diagnosis.CandidateSetReducer;
import checker_reconcile.diagnosis.ConstraintGraph;
import checker_reconcile.diagnosis.CorrectionSet;
import checker_reconcile.diagnosis.CorrectionSetExtractor;
import checker_reconcile.diagnosis.DiagnosisEngine;
import checker_reconcile.diagnosis.DiagnosticGroup;
import checker_reconcile.diagnosis.DiagnosticGrouper;
import checker_reconcile.diagnosis.Mcs;
import checker_reconcile.diagnosis.Mus;
import checker_reconcile.diagnosis.RepairCandidate;
import checker_reconcile.diagnosis.RepairCandidateSet;
import checker_reconcile.diagnosis.RepairCost;
import checker_reconcile.diagnosis.SearchReportJson;
import checker_reconcile.diagnosis.SearchReportParser;
import checker_reconcile.diagnosis.SolverConfig;
import checker_reconcile.diagnosis.ValidationBackedRepairSearch;
import checker_reconcile.experiments.CfTestDiagnosticMiner;
import checker_reconcile.experiments.CfTestDiagnosticMiner.Candidate;
import checker_reconcile.repair.AgentContextJson;
import checker_reconcile.repair.AgentContextParser;
import checker_reconcile.repair.AgentRefactorTargetJson;
import checker_reconcile.repair.AgentRepairRequest;
import checker_reconcile.repair.NoopAgentRepairAdvisor;
import checker_reconcile.repair.PatchApplier;
import checker_reconcile.repair.Patcher;
import checker_reconcile.repair.RepairKind;
import checker_reconcile.repair.RepairPlanJson;
import checker_reconcile.repair.RepairPlanner;
import checker_reconcile.repair.RepairRule;
import checker_reconcile.repair.RepairSketch;
import checker_reconcile.repair.RiskLevel;
import checker_reconcile.repair.SketchOnlyRule;
import checker_reconcile.repair.SourceEdit;
import checker_reconcile.repair.SourceTargetResolver;
import checker_reconcile.repair.SuggestedRepair;
import checker_reconcile.repair.Validation;
import checker_reconcile.trace.TraceEvent;
import checker_reconcile.trace.TraceParser;

/** Tests for the external trace analysis prototype. */
public class TraceParserTest {

    @Test
    public void agentRefactorTargetJsonMapsMaterializationFailures() {
        assertEquals(
                "constructor_delegation",
                AgentRefactorTargetJson.refactorContext("constructor delegation"));
        assertEquals(
                "nested_expression", AgentRefactorTargetJson.refactorContext("nested expression"));
        assertEquals(
                "return_expression", AgentRefactorTargetJson.refactorContext("return expression"));
        assertEquals(
                "throw_expression", AgentRefactorTargetJson.refactorContext("throw expression"));
        assertEquals(
                "lambda_expression", AgentRefactorTargetJson.refactorContext("lambda expression"));
        assertEquals(
                "return_expression",
                AgentRefactorTargetJson.refactorContext(
                        "only sketch or unmaterialized repairs: return expression"));
        assertEquals("", AgentRefactorTargetJson.refactorContext("unsupported source target"));
        assertEquals("", AgentRefactorTargetJson.refactorContext(null));
    }

    @Test
    public void parsesDiagnosticSliceAndTypedSketchPlans() throws Exception {
        Path trace = Files.createTempFile("checker-reconcile", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A1\",\"kind\":\"actual_qualifier\",\"slot\":\"expr:s\",\"type\":\"@Nullable String\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"local:t\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A1\",\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);

        List<TraceEvent> events = new TraceParser().parse(trace);
        TraceModel model = TraceModel.fromEvents(events);
        TraceModel.DiagnosticSlice slice = model.slice("E1");

        assertEquals(Arrays.asList("A1", "A2", "O1"), new Mus().compute(slice));
        assertEquals(Arrays.asList("A1", "A2"), new Mcs().compute(slice));

        List<SuggestedRepair> sketches = new SketchOnlyRule().plan(trace, slice);
        assertTrue(
                sketches.stream()
                        .anyMatch(sketch -> sketch.kind().equals(RepairKind.ADD_NULL_CHECK)));
        assertTrue(
                sketches.stream()
                        .anyMatch(
                                sketch -> sketch.kind().equals(RepairKind.INTRODUCE_SUPPRESSION)));
        assertTrue(sketches.stream().noneMatch(SuggestedRepair::automatic));
    }

    @Test
    public void addNullCheckSketchCarriesExpressionSourceTarget() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-receiver", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.Nullable;",
                        "class Example {",
                        "  void f(@Nullable String s) {",
                        "    s.length();",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        Path trace = Files.createTempFile("checker-reconcile-receiver", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A1\",\"kind\":\"receiver_qualifier\","
                                + "\"slot\":\"receiver:s\",\"type\":\"@Nullable String\","
                                + "\"editable\":false,\"weight\":1000,"
                                + "\"source_target\":{\"kind\":\"receiver_expression\","
                                + "\"expression\":\"s\","
                                + "\"expression_range\":{\"start_offset\":104,"
                                + "\"end_offset\":105}}}",
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"receiver_contract\","
                                + "\"slot\":\"method-contract:nonnull-receiver\","
                                + "\"type\":\"@NonNull String\",\"editable\":false,"
                                + "\"weight\":1000}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"dereference\","
                                + "\"relation\":\"receiver_nonnull\","
                                + "\"got\":\"@Nullable String\",\"want\":\"@NonNull String\","
                                + "\"dependencies\":[\"A1\",\"A2\"],\"result\":\"error\","
                                + "\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\","
                                + "\"error_kind\":\"dereference.of.nullable\","
                                + "\"message\":\"dereference.of.nullable\","
                                + "\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        TraceModel.DiagnosticSlice slice =
                TraceModel.fromEvents(new TraceParser().parse(trace)).slice("E1");

        SuggestedRepair repair = new RepairPlanner().plan(source, slice).get(0);

        assertEquals(RepairKind.ADD_NULL_CHECK, repair.kind());
        assertFalse(repair.automatic());
        assertTrue(repair.edits().isEmpty());
        assertEquals(1, repair.sketches().size());
        RepairSketch sketch = repair.sketches().get(0);
        assertEquals("add_null_check", sketch.kind());
        assertEquals("A1", sketch.targetId());
        assertEquals("receiver_expression", sketch.sourceTargetKind());
        assertEquals("s", sketch.expression());
        assertEquals(Integer.valueOf(104), sketch.startOffset());
        assertEquals(Integer.valueOf(105), sketch.endOffset());

        String plan = new RepairPlanJson().toJson("E1", repair);
        assertTrue(plan.contains("\"sketches\""));
        assertTrue(plan.contains("\"kind\":\"receiver_expression\""));
        assertTrue(plan.contains("\"expression\":\"s\""));

        String context =
                new AgentContextJson()
                        .toJson(source, "E1", slice, Arrays.asList(repair), Arrays.asList(), null);
        assertTrue(context.contains("\"deterministic_repairs\""));
        assertTrue(context.contains("\"sketches\""));
        assertTrue(context.contains("\"expression_range\""));
    }

    @Test
    public void cliPlanCanIncludeNonAutomaticNullCheckEdits() throws Exception {
        String sourceText =
                String.join(
                                System.lineSeparator(),
                                "import org.checkerframework.checker.nullness.qual.Nullable;",
                                "class Example {",
                                "  void f(@Nullable String s) {",
                                "    s.length();",
                                "  }",
                                "}")
                        + System.lineSeparator();
        Path source = Files.createTempFile("checker-reconcile-nullcheck-edit", ".java");
        Files.write(source, sourceText.getBytes(StandardCharsets.UTF_8));
        int expressionStart = sourceText.indexOf("s.length()");
        Path trace = Files.createTempFile("checker-reconcile-nullcheck-edit", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A1\",\"kind\":\"receiver_qualifier\","
                                + "\"slot\":\"receiver:s\",\"type\":\"@Nullable String\","
                                + "\"editable\":false,\"weight\":1000,"
                                + "\"source_target\":{\"kind\":\"receiver_expression\","
                                + "\"expression\":\"s\","
                                + "\"expression_range\":{\"start_offset\":"
                                + expressionStart
                                + ",\"end_offset\":"
                                + (expressionStart + 1)
                                + "}}}",
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"receiver_contract\","
                                + "\"slot\":\"method-contract:nonnull-receiver\","
                                + "\"type\":\"@NonNull String\",\"editable\":false,"
                                + "\"weight\":1000}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"dereference\","
                                + "\"relation\":\"receiver_nonnull\","
                                + "\"got\":\"@Nullable String\",\"want\":\"@NonNull String\","
                                + "\"dependencies\":[\"A1\",\"A2\"],\"result\":\"error\","
                                + "\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\","
                                + "\"error_kind\":\"dereference.of.nullable\","
                                + "\"message\":\"dereference.of.nullable\","
                                + "\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);

        String output =
                captureStdout(
                        "plan",
                        "--source",
                        source.toString(),
                        "--trace",
                        trace.toString(),
                        "--include-sketch-edits");
        List<TraceEvent> plans =
                new TraceParser()
                        .parse(writeTempJsonl("checker-reconcile-nullcheck-edit-plan", output));

        assertEquals(2, plans.size());
        TraceEvent nullCheckPlan = plans.get(0);
        assertEquals("ADD_NULL_CHECK", nullCheckPlan.stringField("kind"));
        assertEquals("false", nullCheckPlan.stringField("automatic"));
        assertEquals(1, nullCheckPlan.listField("edits").size());
        assertTrue(output.contains("if (s == null)"));
        assertTrue(output.contains("throw new NullPointerException"));
        assertTrue(output.contains("\"sketches\""));
    }

    @Test
    public void nullCheckEditPlannerHandlesDottedReceiverStatements() throws Exception {
        String sourceText =
                String.join(
                                System.lineSeparator(),
                                "class Example {",
                                "  void f(Box box) {",
                                "    box.value.toString();",
                                "  }",
                                "}")
                        + System.lineSeparator();
        Path source = Files.createTempFile("checker-reconcile-dotted-nullcheck", ".java");
        Files.write(source, sourceText.getBytes(StandardCharsets.UTF_8));
        int expressionStart = sourceText.indexOf("box.value.toString()");
        SuggestedRepair repair =
                new SuggestedRepair(
                        RepairKind.ADD_NULL_CHECK,
                        Arrays.<SourceEdit>asList(),
                        RiskLevel.BODY_CHANGE,
                        false,
                        Arrays.asList("O1"),
                        "Sketch only in V0.",
                        Arrays.asList(
                                new RepairSketch(
                                        "add_null_check",
                                        "A1",
                                        false,
                                        "Insert a null check guarding box.value.",
                                        "receiver_expression",
                                        "box.value",
                                        expressionStart,
                                        expressionStart + "box.value".length())));

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(source, Arrays.asList(repair))
                        .get(0);

        assertEquals(1, materialized.edits().size());
        assertTrue(materialized.edits().get(0).replacement().contains("if (box.value == null)"));
        assertTrue(
                materialized
                        .edits()
                        .get(0)
                        .replacement()
                        .contains("throw new NullPointerException(\"box.value\")"));
    }

    @Test
    public void nullCheckEditPlannerHandlesSimpleArrayReceiverStatements() throws Exception {
        String sourceText =
                String.join(
                                System.lineSeparator(),
                                "class Example {",
                                "  void f(Object[] values, int i) {",
                                "    values[i].toString();",
                                "  }",
                                "}")
                        + System.lineSeparator();
        Path source = Files.createTempFile("checker-reconcile-array-nullcheck", ".java");
        Files.write(source, sourceText.getBytes(StandardCharsets.UTF_8));
        int expressionStart = sourceText.indexOf("values[i].toString()");
        SuggestedRepair repair =
                new SuggestedRepair(
                        RepairKind.ADD_NULL_CHECK,
                        Arrays.<SourceEdit>asList(),
                        RiskLevel.BODY_CHANGE,
                        false,
                        Arrays.asList("O1"),
                        "Sketch only in V0.",
                        Arrays.asList(
                                new RepairSketch(
                                        "add_null_check",
                                        "A1",
                                        false,
                                        "Insert a null check guarding values[i].",
                                        "receiver_expression",
                                        "values[i]",
                                        expressionStart,
                                        expressionStart + "values[i]".length())));

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(source, Arrays.asList(repair))
                        .get(0);

        assertEquals(1, materialized.edits().size());
        assertTrue(materialized.edits().get(0).replacement().contains("if (values[i] == null)"));
    }

    @Test
    public void nullCheckEditPlannerRejectsComputedArrayReceiverStatements() throws Exception {
        String sourceText =
                String.join(
                                System.lineSeparator(),
                                "class Example {",
                                "  void f(Object[] values, int i) {",
                                "    values[i + 1].toString();",
                                "  }",
                                "}")
                        + System.lineSeparator();
        Path source = Files.createTempFile("checker-reconcile-computed-array-nullcheck", ".java");
        Files.write(source, sourceText.getBytes(StandardCharsets.UTF_8));
        int expressionStart = sourceText.indexOf("values[i + 1].toString()");
        SuggestedRepair repair =
                new SuggestedRepair(
                        RepairKind.ADD_NULL_CHECK,
                        Arrays.<SourceEdit>asList(),
                        RiskLevel.BODY_CHANGE,
                        false,
                        Arrays.asList("O1"),
                        "Sketch only in V0.",
                        Arrays.asList(
                                new RepairSketch(
                                        "add_null_check",
                                        "A1",
                                        false,
                                        "Insert a null check guarding values[i + 1].",
                                        "receiver_expression",
                                        "values[i + 1]",
                                        expressionStart,
                                        expressionStart + "values[i + 1]".length())));

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(source, Arrays.asList(repair))
                        .get(0);

        assertTrue(materialized.edits().isEmpty());
        assertEquals(
                "non-simple expression", materialized.sketches().get(0).materializationFailure());
    }

    @Test
    public void nullCheckEditPlannerHandlesArrayAccessExpressions() throws Exception {
        String sourceText =
                String.join(
                                System.lineSeparator(),
                                "class Example {",
                                "  void f(Object[][] values, int i) {",
                                "    values[i][0] = null;",
                                "  }",
                                "}")
                        + System.lineSeparator();
        Path source = Files.createTempFile("checker-reconcile-array-expression-nullcheck", ".java");
        Files.write(source, sourceText.getBytes(StandardCharsets.UTF_8));
        int expressionStart = sourceText.indexOf("values[i][0]");
        SuggestedRepair repair =
                new SuggestedRepair(
                        RepairKind.ADD_NULL_CHECK,
                        Arrays.<SourceEdit>asList(),
                        RiskLevel.BODY_CHANGE,
                        false,
                        Arrays.asList("O1"),
                        "Sketch only in V0.",
                        Arrays.asList(
                                new RepairSketch(
                                        "add_null_check",
                                        "A1",
                                        false,
                                        "Insert a null check guarding values[i].",
                                        "array_expression",
                                        "values[i]",
                                        expressionStart,
                                        expressionStart + "values[i]".length())));

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(source, Arrays.asList(repair))
                        .get(0);

        assertEquals(1, materialized.edits().size());
        assertTrue(materialized.edits().get(0).replacement().contains("if (values[i] == null)"));
    }

    @Test
    public void nullCheckEditPlannerRejectsArrayExpressionReturnInsertion() throws Exception {
        String sourceText =
                String.join(
                                System.lineSeparator(),
                                "class Example {",
                                "  Object f(Object[][] values, int i) {",
                                "    return values[i][0];",
                                "  }",
                                "}")
                        + System.lineSeparator();
        Path source =
                Files.createTempFile(
                        "checker-reconcile-array-expression-return-nullcheck", ".java");
        Files.write(source, sourceText.getBytes(StandardCharsets.UTF_8));
        int expressionStart = sourceText.indexOf("values[i][0]");
        SuggestedRepair repair =
                new SuggestedRepair(
                        RepairKind.ADD_NULL_CHECK,
                        Arrays.<SourceEdit>asList(),
                        RiskLevel.BODY_CHANGE,
                        false,
                        Arrays.asList("O1"),
                        "Sketch only in V0.",
                        Arrays.asList(
                                new RepairSketch(
                                        "add_null_check",
                                        "A1",
                                        false,
                                        "Insert a null check guarding values[i].",
                                        "array_expression",
                                        "values[i]",
                                        expressionStart,
                                        expressionStart + "values[i]".length())));

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(source, Arrays.asList(repair))
                        .get(0);

        assertTrue(materialized.edits().isEmpty());
        assertEquals("return expression", materialized.sketches().get(0).materializationFailure());
    }

    @Test
    public void nullCheckEditPlannerRejectsReturnExpressionInsertion() throws Exception {
        String sourceText =
                String.join(
                                System.lineSeparator(),
                                "class Example {",
                                "  String f(String s) {",
                                "    return s.toString();",
                                "  }",
                                "}")
                        + System.lineSeparator();
        Path source = Files.createTempFile("checker-reconcile-return-nullcheck", ".java");
        Files.write(source, sourceText.getBytes(StandardCharsets.UTF_8));
        int expressionStart = sourceText.indexOf("s.toString()");
        SuggestedRepair repair =
                new SuggestedRepair(
                        RepairKind.ADD_NULL_CHECK,
                        Arrays.<SourceEdit>asList(),
                        RiskLevel.BODY_CHANGE,
                        false,
                        Arrays.asList("O1"),
                        "Sketch only in V0.",
                        Arrays.asList(
                                new RepairSketch(
                                        "add_null_check",
                                        "A1",
                                        false,
                                        "Insert a null check guarding s.",
                                        "receiver_expression",
                                        "s",
                                        expressionStart,
                                        expressionStart + 1)));

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(source, Arrays.asList(repair))
                        .get(0);

        assertTrue(materialized.edits().isEmpty());
        assertEquals(1, materialized.sketches().size());
        assertEquals("return expression", materialized.sketches().get(0).materializationFailure());

        String plan = new RepairPlanJson().toJson("E1", materialized);
        assertTrue(plan.contains("\"materialization_failure\":\"return expression\""));
        assertTrue(plan.contains("\"agent_refactor_target\":true"));
        assertTrue(plan.contains("\"refactor_context\":\"return_expression\""));
    }

    @Test
    public void nullCheckEditPlannerMaterializesConditionExpression() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-condition-nullcheck", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {", "  void f(Boolean b) {", "    if (b) {}", "  }", "}"),
                StandardCharsets.UTF_8);
        SuggestedRepair repair =
                new SuggestedRepair(
                        RepairKind.ADD_NULL_CHECK,
                        Arrays.<SourceEdit>asList(),
                        RiskLevel.BODY_CHANGE,
                        false,
                        Arrays.asList("O1"),
                        "Sketch only in V0.",
                        Arrays.asList(
                                new RepairSketch(
                                        "add_null_check",
                                        "A1",
                                        false,
                                        "Insert a null check guarding b.",
                                        "condition_expression",
                                        "b",
                                        42,
                                        43)));

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(source, Arrays.asList(repair))
                        .get(0);

        assertEquals(1, materialized.edits().size());
        assertTrue(materialized.edits().get(0).replacement().contains("if (b == null)"));
    }

    @Test
    public void nullCheckEditPlannerMaterializesIterationExpression() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-iteration-nullcheck", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void f(Iterable<String> values) {",
                        "    for (String value : values) {}",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int start = text.indexOf("values) {}");
        SuggestedRepair repair =
                new SuggestedRepair(
                        RepairKind.ADD_NULL_CHECK,
                        Arrays.<SourceEdit>asList(),
                        RiskLevel.BODY_CHANGE,
                        false,
                        Arrays.asList("O1"),
                        "Sketch only in V0.",
                        Arrays.asList(
                                new RepairSketch(
                                        "add_null_check",
                                        "A1",
                                        false,
                                        "Insert a null check guarding values.",
                                        "iteration_expression",
                                        "values",
                                        start,
                                        start + "values".length())));

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(source, Arrays.asList(repair))
                        .get(0);

        assertEquals(1, materialized.edits().size());
        assertTrue(materialized.edits().get(0).replacement().contains("if (values == null)"));
    }

    @Test
    public void nullCheckEditPlannerMaterializesSimpleArgumentInvocation() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-argument-nullcheck", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void takes(String s) {}",
                        "  void f(String s) {",
                        "    takes(s);",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int start = text.indexOf("takes(s);") + "takes(".length();

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(
                                source,
                                Arrays.asList(
                                        nullCheckRepair(
                                                "argument_expression", "s", start, start + 1)))
                        .get(0);

        assertEquals(1, materialized.edits().size());
        assertTrue(materialized.edits().get(0).replacement().contains("if (s == null)"));
    }

    @Test
    public void nullCheckEditPlannerUsesStandaloneArgumentMetadata() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-constructor-nullcheck", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  Example(String s) {}",
                        "  void f(String s) {",
                        "    new Example(s);",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int start = text.indexOf("new Example(s);") + "new Example(".length();
        Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        attributes.put("standalone_invocation", true);
        attributes.put("invocation_kind", "constructor");

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(
                                source,
                                Arrays.asList(
                                        nullCheckRepair(
                                                "argument_expression",
                                                "s",
                                                start,
                                                start + 1,
                                                attributes)))
                        .get(0);

        assertEquals(1, materialized.edits().size());
        assertTrue(materialized.edits().get(0).replacement().contains("if (s == null)"));
    }

    @Test
    public void nullCheckEditPlannerUsesStatementRangeForMultilineArgument() throws Exception {
        Path source =
                Files.createTempFile("checker-reconcile-multiline-argument-nullcheck", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void takes(String s) {}",
                        "  void f(String s) {",
                        "    takes(",
                        "        s);",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int statementStart = text.indexOf("takes(", text.indexOf("void f"));
        int start = text.indexOf("s);");
        Map<String, Object> statementRange = new java.util.LinkedHashMap<>();
        statementRange.put("start_offset", statementStart);
        statementRange.put("end_offset", text.indexOf(";", start) + 1);
        Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        attributes.put("standalone_invocation", true);
        attributes.put("invocation_kind", "method");
        attributes.put("statement_range", statementRange);

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(
                                source,
                                Arrays.asList(
                                        nullCheckRepair(
                                                "argument_expression",
                                                "s",
                                                start,
                                                start + 1,
                                                attributes)))
                        .get(0);

        assertEquals(1, materialized.edits().size());
        assertEquals(statementStart, materialized.edits().get(0).startOffset());
        assertTrue(materialized.edits().get(0).replacement().startsWith("    if (s == null)"));
        assertTrue(
                materialized
                        .edits()
                        .get(0)
                        .replacement()
                        .contains("        throw new NullPointerException(\"s\");"));
    }

    @Test
    public void nullCheckEditPlannerRejectsNonStandaloneArgumentMetadata() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-return-argument-nullcheck", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  String takes(String s) { return s; }",
                        "  String f(String s) {",
                        "    return takes(s);",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int start = text.indexOf("takes(s);") + "takes(".length();
        Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        attributes.put("standalone_invocation", false);
        attributes.put("invocation_kind", "method");

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(
                                source,
                                Arrays.asList(
                                        nullCheckRepair(
                                                "argument_expression",
                                                "s",
                                                start,
                                                start + 1,
                                                attributes)))
                        .get(0);

        assertTrue(materialized.edits().isEmpty());
        assertEquals("return expression", materialized.sketches().get(0).materializationFailure());
    }

    @Test
    public void nullCheckEditPlannerRejectsConstructorDelegationArgument() throws Exception {
        Path source =
                Files.createTempFile("checker-reconcile-constructor-argument-nullcheck", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  Example(String s) {}",
                        "  Example(String s, int i) {",
                        "    this(s);",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int start = text.indexOf("this(s);") + "this(".length();

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(
                                source,
                                Arrays.asList(
                                        nullCheckRepair(
                                                "argument_expression", "s", start, start + 1)))
                        .get(0);

        assertTrue(materialized.edits().isEmpty());
        assertEquals(
                "constructor delegation", materialized.sketches().get(0).materializationFailure());
    }

    @Test
    public void nullCheckEditPlannerRejectsNonInvocationArgumentContext() throws Exception {
        Path source =
                Files.createTempFile("checker-reconcile-assignment-argument-nullcheck", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  String takes(String s) { return s; }",
                        "  void f(String s) {",
                        "    String value = takes(s);",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int start = text.indexOf("takes(s);") + "takes(".length();

        SuggestedRepair materialized =
                new checker_reconcile.repair.NullCheckEditPlanner()
                        .addNullCheckEdits(
                                source,
                                Arrays.asList(
                                        nullCheckRepair(
                                                "argument_expression", "s", start, start + 1)))
                        .get(0);

        assertTrue(materialized.edits().isEmpty());
        assertEquals("nested expression", materialized.sketches().get(0).materializationFailure());
    }

    @Test
    public void sourceTargetResolverDoesNotInferNonLocalRiskFromRange() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-parameter-target", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.NonNull;",
                        "class Example {",
                        "  void takes(@NonNull String p) {}",
                        "}"),
                StandardCharsets.UTF_8);
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int start = text.indexOf("@NonNull String p");
        int end = text.indexOf(") {}", start);
        Map<String, Object> range = new java.util.LinkedHashMap<>();
        range.put("start_offset", start);
        range.put("end_offset", end);
        range.put("start_line", 3);
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("event", "assumption");
        fields.put("id", "A1");
        fields.put("slot", "parameter:p");
        fields.put("range", range);
        TraceEvent assumption = new TraceEvent(1, fields);

        assertTrue(
                new SourceTargetResolver().resolveAnnotationTarget(source, assumption, "@NonNull")
                        == null);
    }

    @Test
    public void reportsInvalidJsonLineNumber() throws Exception {
        Path trace = Files.createTempFile("checker-reconcile-invalid", ".jsonl");
        Files.write(
                trace, Arrays.asList("{\"event\":\"assumption\"}", "{bad"), StandardCharsets.UTF_8);

        try {
            new TraceParser().parse(trace);
            fail("expected parse failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("trace line 2"));
        }
    }

    @Test
    public void preservesUnknownFutureEvents() throws Exception {
        Path trace = Files.createTempFile("checker-reconcile-future", ".jsonl");
        Files.write(
                trace,
                Arrays.asList("{\"event\":\"future_event\",\"id\":\"F1\",\"payload\":1}"),
                StandardCharsets.UTF_8);

        List<TraceEvent> events = new TraceParser().parse(trace);

        assertEquals(1, events.size());
        assertEquals("future_event", events.get(0).event);
        assertEquals("F1", events.get(0).id);
    }

    @Test
    public void checksNullnessCompatibility() {
        assertTrue(Nullness.isSubtype("@NonNull String", "@Nullable String"));
        assertTrue(Nullness.isSubtype("@NonNull String", "@NonNull String"));
        assertFalse(Nullness.isSubtype("@Nullable String", "@NonNull String"));
        assertTrue(Nullness.receiverNonNull("@NonNull Object"));
        assertFalse(Nullness.receiverNonNull("@Nullable Object"));
    }

    @Test
    public void buildsConstraintGraphForDiagnosticTrace() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));

        ConstraintGraph graph = ConstraintGraph.fromModel(model);

        assertTrue(graph.nodes().containsKey("A1"));
        assertTrue(graph.nodes().containsKey("A2"));
        assertTrue(graph.nodes().containsKey("O1"));
        assertTrue(graph.nodes().containsKey("E1"));
        assertTrue(graph.nodes().containsKey("slot:local:t"));
        assertTrue(graph.nodes().containsKey("qualifier:@NonNull String"));
        assertEquals(8, graph.nodes().size());
        assertEquals(7, graph.edges().size());
    }

    @Test
    public void boundedSolverRanksAutomaticLocalRepairFirst() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));
        TraceModel.DiagnosticSlice slice = model.slice("E1");

        List<SuggestedRepair> repairs = new RepairPlanner().plan(source, slice);
        List<RepairCandidateSet> candidates =
                new BoundedDiagnosisSolver()
                        .solve(
                                ConstraintGraph.fromModel(model),
                                slice,
                                repairs,
                                new SolverConfig(2, 20));

        assertFalse(candidates.isEmpty());
        assertEquals(1, candidates.get(0).candidates().size());
        assertEquals(RepairKind.CHANGE_QUALIFIER, candidates.get(0).candidates().get(0).kind());
        assertEquals(Arrays.asList("A2"), candidates.get(0).candidates().get(0).assumptionIds());
        assertEquals(5, candidates.get(0).cost().value());
    }

    @Test
    public void extractsBoundedCorrectionSetsFromEditableAssumptions() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));

        List<CorrectionSet> correctionSets =
                new CorrectionSetExtractor().extract(model.slice("E1"), new SolverConfig(2, 20));

        assertEquals(Arrays.asList("A1"), correctionSets.get(0).assumptionIds());
        assertEquals(Arrays.asList("A2"), correctionSets.get(1).assumptionIds());
        assertEquals(Arrays.asList("A1", "A2"), correctionSets.get(2).assumptionIds());
    }

    @Test
    public void plannerCanRestrictRepairsToCorrectionSetAssumptions() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));
        TraceModel.DiagnosticSlice slice = model.slice("E1");

        List<SuggestedRepair> actualOnly =
                new RepairPlanner()
                        .plan(source, slice, new CorrectionSet(Arrays.asList("A1"), null));
        List<SuggestedRepair> targetOnly =
                new RepairPlanner()
                        .plan(source, slice, new CorrectionSet(Arrays.asList("A2"), null));

        assertTrue(actualOnly.isEmpty());
        assertEquals(1, targetOnly.size());
        assertEquals(RepairKind.CHANGE_QUALIFIER, targetOnly.get(0).kind());
        assertEquals(Arrays.asList("A2", "O1", "E1"), targetOnly.get(0).evidenceIds());
    }

    @Test
    public void diagnosticGrouperKeepsUnrelatedLocalsSeparate() throws Exception {
        Path source = sourceWithTwoExplicitLocals();
        Path trace = traceForTwoExplicitLocals(source);
        TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));

        List<DiagnosticGroup> groups = new DiagnosticGrouper().group(model);

        assertEquals(2, groups.size());
        assertEquals(Arrays.asList("E1"), groups.get(0).diagnosticIds());
        assertEquals(Arrays.asList("E2"), groups.get(1).diagnosticIds());
    }

    @Test
    public void diagnosticGrouperConnectsSharedSlots() throws Exception {
        Path trace = traceWithSharedFieldSlot();
        TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));

        List<DiagnosticGroup> groups = new DiagnosticGrouper().group(model);

        assertEquals(1, groups.size());
        assertEquals(Arrays.asList("E1", "E2"), groups.get(0).diagnosticIds());
        assertTrue(groups.get(0).keys().contains("slot:field:f"));
    }

    @Test
    public void diagnosisEngineReturnsRankedCandidateSets() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));

        List<RepairCandidateSet> candidates =
                new DiagnosisEngine().diagnose(source, model, model.slice("E1"));

        assertFalse(candidates.isEmpty());
        assertEquals(RepairKind.CHANGE_QUALIFIER, candidates.get(0).candidates().get(0).kind());
    }

    @Test
    public void candidateReducerKeepsCheapestEquivalentInsight() throws Exception {
        Path source = sourceWithExplicitLocal();
        RepairCandidateSet cheap =
                candidateSet(repair("same", "A2", new SourceEdit(source, 10, 12, "cheap")), 5);
        RepairCandidateSet expensive =
                candidateSet(repair("same", "A2", new SourceEdit(source, 10, 12, "expensive")), 50);

        List<RepairCandidateSet> reduced =
                new CandidateSetReducer().reduce(Arrays.asList(expensive, cheap));

        assertEquals(1, reduced.size());
        assertEquals(5, reduced.get(0).cost().value());
    }

    @Test
    public void candidateReducerDropsSetsWithNoNewCoverage() throws Exception {
        Path source = sourceWithExplicitLocal();
        RepairCandidateSet first =
                candidateSet(repair("first", "A1", new SourceEdit(source, 10, 12, "first")), 5);
        RepairCandidateSet redundant =
                candidateSet(
                        repair("redundant", "A1", new SourceEdit(source, 10, 12, "redundant")), 6);
        RepairCandidateSet second =
                candidateSet(repair("second", "A2", new SourceEdit(source, 20, 22, "second")), 7);

        List<RepairCandidateSet> reduced =
                new CandidateSetReducer().reduce(Arrays.asList(first, redundant, second));

        assertEquals(2, reduced.size());
        assertEquals("first", reduced.get(0).candidates().get(0).message());
        assertEquals("second", reduced.get(1).candidates().get(0).message());
    }

    @Test
    public void candidateReducerDropsMultiRepairEmptyEditSets() {
        SuggestedRepair sketch =
                new SuggestedRepair(
                        RepairKind.ADD_NULL_CHECK,
                        Arrays.<SourceEdit>asList(),
                        RiskLevel.BODY_CHANGE,
                        false,
                        Arrays.asList("O1"),
                        "sketch");
        SuggestedRepair suppression =
                new SuggestedRepair(
                        RepairKind.INTRODUCE_SUPPRESSION,
                        Arrays.<SourceEdit>asList(),
                        RiskLevel.SUPPRESSION,
                        false,
                        Arrays.asList("E1"),
                        "suppression");
        RepairCandidateSet sketchOnly = candidateSet(sketch, 10);
        RepairCandidateSet suppressionOnly = candidateSet(suppression, 20);
        RepairCandidateSet combined =
                new RepairCandidateSet(
                        Arrays.asList(
                                new RepairCandidate(
                                        sketch,
                                        Arrays.<String>asList(),
                                        Arrays.asList("E1"),
                                        new RepairCost(10)),
                                new RepairCandidate(
                                        suppression,
                                        Arrays.<String>asList(),
                                        Arrays.asList("E1"),
                                        new RepairCost(20))));

        List<RepairCandidateSet> reduced =
                new CandidateSetReducer()
                        .reduce(Arrays.asList(combined, sketchOnly, suppressionOnly));

        assertEquals(2, reduced.size());
        assertEquals("sketch", reduced.get(0).candidates().get(0).message());
        assertEquals("suppression", reduced.get(1).candidates().get(0).message());
    }

    @Test
    public void boundedSolverRejectsOverlappingCandidateSets() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));
        TraceModel.DiagnosticSlice slice = model.slice("E1");
        List<SuggestedRepair> repairs =
                Arrays.asList(
                        repair("first", "A1", new SourceEdit(source, 10, 20, "first")),
                        repair("second", "A2", new SourceEdit(source, 15, 25, "second")));

        List<RepairCandidateSet> candidates =
                new BoundedDiagnosisSolver()
                        .solve(
                                ConstraintGraph.fromModel(model),
                                slice,
                                repairs,
                                new SolverConfig(2, 20));

        assertFalse(hasCandidateSetWithMessages(candidates, "first", "second"));
    }

    @Test
    public void boundedSolverRejectsDuplicateAssumptionCandidateSets() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));
        TraceModel.DiagnosticSlice slice = model.slice("E1");
        List<SuggestedRepair> repairs =
                Arrays.asList(
                        repair("first", "A2", new SourceEdit(source, 10, 12, "first")),
                        repair("second", "A2", new SourceEdit(source, 20, 22, "second")));

        List<RepairCandidateSet> candidates =
                new BoundedDiagnosisSolver()
                        .solve(
                                ConstraintGraph.fromModel(model),
                                slice,
                                repairs,
                                new SolverConfig(2, 20));

        assertFalse(hasCandidateSetWithMessages(candidates, "first", "second"));
    }

    @Test
    public void boundedSolverDoesNotMixSuppressionWithConcreteRepair() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));
        TraceModel.DiagnosticSlice slice = model.slice("E1");
        List<SuggestedRepair> repairs =
                Arrays.asList(
                        repair("concrete", "A2", new SourceEdit(source, 10, 12, "concrete")),
                        new SuggestedRepair(
                                RepairKind.INTRODUCE_SUPPRESSION,
                                Arrays.<SourceEdit>asList(),
                                RiskLevel.SUPPRESSION,
                                false,
                                Arrays.asList("A1"),
                                "suppression"));

        List<RepairCandidateSet> candidates =
                new BoundedDiagnosisSolver()
                        .solve(
                                ConstraintGraph.fromModel(model),
                                slice,
                                repairs,
                                new SolverConfig(2, 20));

        assertFalse(hasCandidateSetWithMessages(candidates, "concrete", "suppression"));
    }

    @Test
    public void patchesExplicitLocalAnnotationOnly() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));
        TraceModel.DiagnosticSlice slice = model.slice("E1");
        Path patched = Files.createTempFile("checker-reconcile-patched", ".java");

        List<SuggestedRepair> repairs = new RepairPlanner().plan(source, slice);
        assertEquals(RepairKind.CHANGE_QUALIFIER, repairs.get(0).kind());
        assertTrue(repairs.get(0).automatic());
        assertEquals("@Nullable", repairs.get(0).edits().get(0).replacement());

        new Patcher().writePatched(source, patched, slice);

        String patchedText = new String(Files.readAllBytes(patched), StandardCharsets.UTF_8);
        assertTrue(patchedText.contains("@Nullable String t = s;"));
        assertFalse(patchedText.contains("@SuppressWarnings"));
    }

    @Test
    public void doesNotAutomaticallyRepairWithoutExplicitSourceTarget() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-offset-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void f(String s) {",
                        "    @NonNull String t = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int declarationStart = text.indexOf("@NonNull String t");
        int declarationEnd = text.indexOf(";", declarationStart) + 1;
        Path trace = Files.createTempFile("checker-reconcile-offset", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"local:t\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,\"range\":{\"start_offset\":"
                                + declarationStart
                                + ",\"end_offset\":"
                                + declarationEnd
                                + ",\"start_line\":3,\"start_col\":5,\"end_line\":3,\"end_col\":28}}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        TraceModel.DiagnosticSlice slice =
                TraceModel.fromEvents(new TraceParser().parse(trace)).slice("E1");

        List<SuggestedRepair> repairs = new RepairPlanner().plan(source, slice);

        assertTrue(
                repairs.stream()
                        .noneMatch(
                                repair ->
                                        repair.kind() == RepairKind.CHANGE_QUALIFIER
                                                && repair.automatic()));
    }

    @Test
    public void plansLocalAnnotationRepairFromExplicitSourceTarget() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-source-target", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void f(String s) {",
                        "    @NonNull String t = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = text.indexOf("@NonNull");
        int annotationEnd = annotationStart + "@NonNull".length();
        Path trace = Files.createTempFile("checker-reconcile-source-target-trace", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"local:t\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,\"source_target\":{\"kind\":\"local_annotation\",\"annotation\":\"@NonNull\",\"annotation_range\":{\"start_offset\":"
                                + annotationStart
                                + ",\"end_offset\":"
                                + annotationEnd
                                + "}}}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        TraceModel.DiagnosticSlice slice =
                TraceModel.fromEvents(new TraceParser().parse(trace)).slice("E1");

        SourceEdit edit = new RepairPlanner().plan(source, slice).get(0).edits().get(0);

        assertEquals(annotationStart, edit.startOffset());
        assertEquals(annotationEnd, edit.endOffset());
        assertEquals("@Nullable", edit.replacement());
    }

    @Test
    public void plansApiChangeRepairsFromExplicitSourceTargets() throws Exception {
        assertApiChangeRepair("field_annotation", "field:f");
        assertApiChangeRepair("parameter_annotation", "parameter:p");
        assertApiChangeRepair("return_annotation", "return");
    }

    @Test
    public void graphDiagnosisRepairsNestedQualifierAtSourceTargetPosition() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-nested-parameter", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void takes(@NonNull String[] values) {}",
                        "  void f(String[] values) { takes(values); }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = text.indexOf("@NonNull");
        Path trace = Files.createTempFile("checker-reconcile-nested-parameter-trace", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A1\",\"kind\":\"actual_qualifier\",\"slot\":\"expr:values\",\"type\":\"@Nullable String @NonNull []\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"parameter:values\",\"type\":\"@NonNull String @Nullable []\",\"editable\":true,\"weight\":5,\"source_target\":{\"kind\":\"parameter_annotation\",\"annotation\":\"@NonNull\",\"annotation_range\":{\"start_offset\":"
                                + annotationStart
                                + ",\"end_offset\":"
                                + (annotationStart + "@NonNull".length())
                                + "}}}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"method_argument\",\"relation\":\"subtype\",\"got\":\"@Nullable String @NonNull []\",\"want\":\"@NonNull String @Nullable []\",\"dependencies\":[\"A1\",\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"argument.type.incompatible\",\"message\":\"argument.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));

        List<RepairCandidateSet> candidates =
                new DiagnosisEngine().diagnose(source, model, model.slice("E1"));

        assertEquals(RepairKind.CHANGE_QUALIFIER, candidates.get(0).candidates().get(0).kind());
        assertEquals(
                "@Nullable",
                candidates.get(0).candidates().get(0).repair().edits().get(0).replacement());
    }

    private void assertApiChangeRepair(String sourceTargetKind, String slot) throws Exception {
        Path source = Files.createTempFile("checker-reconcile-api-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  @NonNull String f;",
                        "  @NonNull String m(@NonNull String p) { return p; }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart =
                sourceTargetKind.equals("parameter_annotation")
                        ? text.lastIndexOf("@NonNull")
                        : text.indexOf("@NonNull");
        if (sourceTargetKind.equals("return_annotation")) {
            annotationStart = text.indexOf("@NonNull", text.indexOf("String m") - 12);
        }
        Path trace = Files.createTempFile("checker-reconcile-api-trace", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\""
                                + slot
                                + "\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,\"source_target\":{\"kind\":\""
                                + sourceTargetKind
                                + "\",\"annotation\":\"@NonNull\",\"annotation_range\":{\"start_offset\":"
                                + annotationStart
                                + ",\"end_offset\":"
                                + (annotationStart + "@NonNull".length())
                                + "}}}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        TraceModel.DiagnosticSlice slice =
                TraceModel.fromEvents(new TraceParser().parse(trace)).slice("E1");

        SuggestedRepair repair = new RepairPlanner().plan(source, slice).get(0);

        assertEquals(RepairKind.CHANGE_QUALIFIER, repair.kind());
        assertEquals(RiskLevel.API_CHANGE, repair.risk());
        assertTrue(repair.automatic());
        assertEquals("@Nullable", repair.edits().get(0).replacement());
    }

    @Test
    public void serializesSuggestedRepairAsJsonl() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-json-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void f(String s) {",
                        "    @NonNull String t = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = text.indexOf("@NonNull");
        Path trace = Files.createTempFile("checker-reconcile-json-trace", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"local:t\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,\"source_target\":{\"kind\":\"local_annotation\",\"annotation\":\"@NonNull\",\"annotation_range\":{\"start_offset\":"
                                + annotationStart
                                + ",\"end_offset\":"
                                + (annotationStart + "@NonNull".length())
                                + "}}}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        TraceModel.DiagnosticSlice slice =
                TraceModel.fromEvents(new TraceParser().parse(trace)).slice("E1");
        SuggestedRepair repair = new RepairPlanner().plan(source, slice).get(0);
        Path plan = Files.createTempFile("checker-reconcile-plan", ".jsonl");

        Files.write(
                plan,
                Arrays.asList(new RepairPlanJson().toJson("E1", repair)),
                StandardCharsets.UTF_8);
        TraceEvent event = new TraceParser().parse(plan).get(0);

        assertEquals("1", event.stringField("schema_version"));
        assertEquals("E1", event.stringField("diagnostic_id"));
        assertEquals("CHANGE_QUALIFIER", event.stringField("kind"));
        assertEquals("LOCAL_ONLY", event.stringField("risk"));
        assertTrue(Boolean.parseBoolean(event.stringField("automatic")));
        assertEquals(1, event.listField("edits").size());
    }

    @Test
    public void cliAgentContextExportsDiagnosticRepairsAndSearchReport() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        Path report =
                writeTempJsonl(
                        "checker-reconcile-agent-search-report",
                        "{\"schema_version\":1,\"event\":\"summary\",\"accepted\":true,"
                                + "\"before_diagnostic_count\":1,\"before_exit_code\":1,"
                                + "\"after_diagnostic_count\":0,\"after_exit_code\":0,"
                                + "\"max_candidate_size\":1,\"max_search_candidates\":1,"
                                + "\"generated_candidate_count\":1,"
                                + "\"searched_candidate_count\":1,"
                                + "\"pruned_empty_edit_count\":0,"
                                + "\"pruned_empty_edit_reasons\":{},"
                                + "\"pruned_duplicate_edit_count\":0,"
                                + "\"pruned_overlap_count\":0,"
                                + "\"pruned_budget_count\":0,"
                                + "\"all_diagnostic_ids\":[\"E1\"],"
                                + "\"validated_diagnostic_ids\":[\"E1\"],"
                                + "\"accepted_diagnostic_ids\":[\"E1\"],"
                                + "\"rejected_diagnostic_ids\":[],"
                                + "\"skipped_diagnostic_ids\":[],"
                                + "\"uncovered_diagnostic_ids\":[],"
                                + "\"accepted_candidate_cost\":5,"
                                + "\"accepted_candidate_size\":1}");

        String output =
                captureStdout(
                        "agent-context",
                        "--source",
                        source.toString(),
                        "--trace",
                        trace.toString(),
                        "--diagnostic",
                        "E1",
                        "--search-report",
                        report.toString());
        TraceEvent event =
                new AgentContextParser()
                        .parse(writeTempJsonl("checker-reconcile-agent-context", output));

        assertEquals("agent_context", event.stringField("event"));
        assertEquals("1", event.stringField("schema_version"));
        assertEquals("E1", event.stringField("diagnostic_id"));
        assertEquals(2, event.listField("assumptions").size());
        assertFalse(event.listField("deterministic_repairs").isEmpty());
        assertEquals(1, event.listField("search_report").size());
        assertTrue(output.contains("\"search_summary\""));
        assertTrue(output.contains("\"validation_result\":{}"));
        assertTrue(output.contains("\"accepted_diagnostic_ids\":[\"E1\"]"));
        assertTrue(output.contains("\"rejected_diagnostic_ids\":[]"));
        assertTrue(output.contains("\"skipped_diagnostic_ids\":[]"));
        assertTrue(output.contains("\"kind\":\"CHANGE_QUALIFIER\""));
        assertTrue(output.contains("\"replacement\":\"@Nullable\""));
    }

    @Test
    public void cliAgentRepairEmitsPlansFromAgentContext() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        String contextOutput =
                captureStdout(
                        "agent-context",
                        "--source",
                        source.toString(),
                        "--trace",
                        trace.toString(),
                        "--diagnostic",
                        "E1");
        Path context = writeTempJsonl("checker-reconcile-agent-repair-context", contextOutput);

        String repairOutput =
                captureStdout(
                        "agent-repair",
                        "--context",
                        context.toString(),
                        "--automatic-only",
                        "--allow-risk",
                        "LOCAL_ONLY");
        List<TraceEvent> plans =
                new TraceParser()
                        .parse(writeTempJsonl("checker-reconcile-agent-repair-plan", repairOutput));

        assertEquals(1, plans.size());
        assertEquals("1", plans.get(0).stringField("schema_version"));
        assertEquals("E1", plans.get(0).stringField("diagnostic_id"));
        assertEquals("CHANGE_QUALIFIER", plans.get(0).stringField("kind"));
        assertEquals("LOCAL_ONLY", plans.get(0).stringField("risk"));
        assertEquals(1, plans.get(0).listField("edits").size());
        assertTrue(repairOutput.contains("\"replacement\":\"@Nullable\""));
    }

    @Test
    public void cliAgentContextEmbedsValidationReport() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        Path report =
                writeTempJsonl(
                        "checker-reconcile-agent-validation-result",
                        "{\"schema_version\":1,\"event\":\"validation_result\","
                                + "\"source\":\""
                                + source
                                + "\",\"patched_source\":\"Patched.java\","
                                + "\"validation_mode\":\"pass\",\"accepted\":true,"
                                + "\"applied_plan_count\":1,\"applied_edit_plan_count\":1,"
                                + "\"diagnostic_ids\":[\"E1\"],"
                                + "\"agent_origin_count\":1,"
                                + "\"validation_required_count\":1,"
                                + "\"after_exit_code\":0,"
                                + "\"after_diagnostic_count\":0}");

        String output =
                captureStdout(
                        "agent-context",
                        "--source",
                        source.toString(),
                        "--trace",
                        trace.toString(),
                        "--diagnostic",
                        "E1",
                        "--validation-report",
                        report.toString());
        TraceEvent event =
                new AgentContextParser()
                        .parse(
                                writeTempJsonl(
                                        "checker-reconcile-agent-context-validation-result",
                                        output));

        assertEquals("agent_context", event.stringField("event"));
        assertTrue(output.contains("\"validation_result\""));
        assertTrue(output.contains("\"event\":\"validation_result\""));
        assertTrue(output.contains("\"diagnostic_ids\":[\"E1\"]"));
    }

    @Test
    public void agentContextParserRejectsMalformedBundle() throws Exception {
        Path context =
                writeTempJsonl(
                        "checker-reconcile-bad-agent-context",
                        "{\"schema_version\":1,\"event\":\"agent_context\",\"source\":\"Example.java\",\"diagnostic_id\":\"E1\",\"diagnostic\":{},\"obligation\":{},\"assumptions\":[],\"deterministic_repairs\":[],\"search_summary\":{},\"search_report\":[],\"validation_result\":{}}");

        try {
            new AgentContextParser().parse(context);
            fail("expected malformed agent context rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("diagnostic missing event"));
        }
    }

    @Test
    public void agentContextParserRejectsMismatchedValidationReport() throws Exception {
        Path context =
                writeTempJsonl(
                        "checker-reconcile-mismatched-validation-context",
                        "{\"schema_version\":1,\"event\":\"agent_context\","
                                + "\"source\":\"Example.java\",\"diagnostic_id\":\"E1\","
                                + "\"diagnostic\":{\"event\":\"diagnostic\",\"id\":\"E1\"},"
                                + "\"obligation\":{\"event\":\"obligation\",\"id\":\"O1\"},"
                                + "\"assumptions\":[],\"deterministic_repairs\":[],"
                                + "\"search_summary\":{},\"search_report\":[],"
                                + "\"validation_result\":{\"schema_version\":1,"
                                + "\"event\":\"validation_result\","
                                + "\"source\":\"Example.java\","
                                + "\"patched_source\":\"Patched.java\","
                                + "\"validation_mode\":\"pass\","
                                + "\"accepted\":true,"
                                + "\"applied_plan_count\":1,"
                                + "\"applied_edit_plan_count\":1,"
                                + "\"diagnostic_ids\":[\"E2\"],"
                                + "\"agent_origin_count\":1,"
                                + "\"validation_required_count\":1,"
                                + "\"after_exit_code\":0,"
                                + "\"after_diagnostic_count\":0}}");

        try {
            new AgentContextParser().parse(context);
            fail("expected validation-result diagnostic mismatch rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must include context diagnostic E1"));
        }
    }

    @Test
    public void cliAgentContextRejectsMalformedValidationReport() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        Path report =
                writeTempJsonl(
                        "checker-reconcile-malformed-validation-result",
                        "{\"schema_version\":1,\"event\":\"validation_result\","
                                + "\"source\":\""
                                + source
                                + "\",\"patched_source\":\"Patched.java\","
                                + "\"validation_mode\":\"pass\",\"accepted\":true,"
                                + "\"applied_plan_count\":1,"
                                + "\"applied_edit_plan_count\":1,"
                                + "\"diagnostic_ids\":[\"E1\"],"
                                + "\"agent_origin_count\":1,"
                                + "\"validation_required_count\":1}");

        try {
            captureStdout(
                    "agent-context",
                    "--source",
                    source.toString(),
                    "--trace",
                    trace.toString(),
                    "--diagnostic",
                    "E1",
                    "--validation-report",
                    report.toString());
            fail("expected malformed validation report rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("missing numeric after_exit_code"));
        }
    }

    @Test
    public void cliPlansAndAppliesAutomaticJsonPlan() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-cli-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void f(String s) {",
                        "    @NonNull String t = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = text.indexOf("@NonNull");
        Path trace = Files.createTempFile("checker-reconcile-cli-trace", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"local:t\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,\"source_target\":{\"kind\":\"local_annotation\",\"annotation\":\"@NonNull\",\"annotation_range\":{\"start_offset\":"
                                + annotationStart
                                + ",\"end_offset\":"
                                + (annotationStart + "@NonNull".length())
                                + "}}}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        Path plan = Files.createTempFile("checker-reconcile-cli-plan", ".jsonl");
        Path patched = Files.createTempFile("checker-reconcile-cli-patched", ".java");
        Path filteredPatched =
                Files.createTempFile("checker-reconcile-cli-filtered-patched", ".java");
        Path rejectedPatched =
                Files.createTempFile("checker-reconcile-cli-rejected-patched", ".java");
        Path dryRunPatched =
                Files.createTempDirectory("checker-reconcile-cli-dry-run").resolve("Patched.java");

        String planOutput =
                captureStdout("plan", "--source", source.toString(), "--trace", trace.toString());
        Files.write(plan, Arrays.asList(planOutput.trim().split("\\R")), StandardCharsets.UTF_8);
        TraceEvent planEvent = new TraceParser().parse(plan).get(0);
        assertEquals("1", planEvent.stringField("schema_version"));
        assertEquals("CHANGE_QUALIFIER", planEvent.stringField("kind"));
        assertEquals("LOCAL_ONLY", planEvent.stringField("risk"));
        assertTrue(Boolean.parseBoolean(planEvent.stringField("automatic")));

        captureStdout(
                "apply-plan",
                "--source",
                source.toString(),
                "--plan",
                plan.toString(),
                "--out",
                patched.toString());

        String patchedText = new String(Files.readAllBytes(patched), StandardCharsets.UTF_8);
        assertTrue(patchedText.contains("@Nullable String t = s;"));

        captureStdout(
                "apply-plan",
                "--source",
                source.toString(),
                "--plan",
                plan.toString(),
                "--out",
                filteredPatched.toString(),
                "--diagnostic",
                "E1",
                "--kind",
                "CHANGE_QUALIFIER",
                "--allow-risk",
                "LOCAL_ONLY");
        String filteredPatchedText =
                new String(Files.readAllBytes(filteredPatched), StandardCharsets.UTF_8);
        assertTrue(filteredPatchedText.contains("@Nullable String t = s;"));

        captureStdout(
                "apply-plan",
                "--source",
                source.toString(),
                "--plan",
                plan.toString(),
                "--out",
                rejectedPatched.toString(),
                "--allow-risk",
                "AGENT_ASSISTED");
        String rejectedPatchedText =
                new String(Files.readAllBytes(rejectedPatched), StandardCharsets.UTF_8);
        assertTrue(rejectedPatchedText.contains("@NonNull String t = s;"));
        assertFalse(rejectedPatchedText.contains("@Nullable String t = s;"));

        String dryRunOutput =
                captureStdout(
                        "apply-plan",
                        "--source",
                        source.toString(),
                        "--plan",
                        plan.toString(),
                        "--out",
                        dryRunPatched.toString(),
                        "--diagnostic",
                        "E1",
                        "--dry-run");
        assertFalse(Files.exists(dryRunPatched));
        TraceEvent dryRunEvent =
                new TraceParser()
                        .parse(writeTempJsonl("checker-reconcile-cli-dry-run-plan", dryRunOutput))
                        .get(0);
        assertEquals("1", dryRunEvent.stringField("schema_version"));
        assertEquals("CHANGE_QUALIFIER", dryRunEvent.stringField("kind"));
        assertEquals(1, dryRunEvent.listField("edits").size());
    }

    @Test
    public void applyPlanRequiresValidationForAgentAutomaticEdits() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-agent-plan-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void f(String s) {",
                        "    @NonNull String t = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = text.indexOf("@NonNull");
        String planJson =
                "{\"schema_version\":1,\"origin\":\"agent\",\"confidence\":0.72,"
                        + "\"requires_validation\":true,"
                        + "\"diagnostic_id\":\"E1\",\"kind\":\"CHANGE_QUALIFIER\","
                        + "\"risk\":\"LOCAL_ONLY\",\"automatic\":true,"
                        + "\"message\":\"agent proposed qualifier weakening\","
                        + "\"evidence_ids\":[\"A2\"],\"edits\":[{\"start_offset\":"
                        + annotationStart
                        + ",\"end_offset\":"
                        + (annotationStart + "@NonNull".length())
                        + ",\"replacement\":\"@Nullable\"}]}";
        Path plan = writeTempJsonl("checker-reconcile-agent-plan", planJson);
        Path rejected = Files.createTempFile("checker-reconcile-agent-plan-rejected", ".java");

        try {
            captureStdout(
                    "apply-plan",
                    "--source",
                    source.toString(),
                    "--plan",
                    plan.toString(),
                    "--out",
                    rejected.toString());
            fail("expected agent plan to require validation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("requires --validate"));
        }

        Path patched = Files.createTempFile("checker-reconcile-agent-plan-patched", ".java");
        Path report = Files.createTempFile("checker-reconcile-agent-validation-report", ".jsonl");
        String output =
                captureStdout(
                        "apply-plan",
                        "--source",
                        source.toString(),
                        "--plan",
                        plan.toString(),
                        "--out",
                        patched.toString(),
                        "--validate",
                        "--validation-report",
                        report.toString(),
                        "--javac",
                        fakeJavacAcceptingNullableLocal().toString());

        String patchedText = new String(Files.readAllBytes(patched), StandardCharsets.UTF_8);
        TraceEvent reportEvent = new TraceParser().parse(report).get(0);
        assertTrue(output.contains("validation accepted"));
        assertTrue(patchedText.contains("@Nullable String t = s;"));
        assertEquals("validation_result", reportEvent.stringField("event"));
        assertEquals("pass", reportEvent.stringField("validation_mode"));
        assertTrue(Boolean.parseBoolean(reportEvent.stringField("accepted")));
        assertEquals("1", reportEvent.stringField("applied_plan_count"));
        assertEquals("1", reportEvent.stringField("applied_edit_plan_count"));
        assertEquals("1", reportEvent.stringField("agent_origin_count"));
        assertEquals("1", reportEvent.stringField("validation_required_count"));
        assertEquals("0", reportEvent.stringField("after_exit_code"));
        assertEquals("0", reportEvent.stringField("after_diagnostic_count"));
        assertTrue(reportEvent.listField("diagnostic_ids").contains("E1"));
    }

    @Test
    public void applyPlanWritesDecreaseValidationReport() throws Exception {
        Path source = sourceWithTwoExplicitLocals();
        Path trace = traceForTwoExplicitLocals(source);
        Path plan = Files.createTempFile("checker-reconcile-decrease-report-plan", ".jsonl");
        Path patched = Files.createTempFile("checker-reconcile-decrease-report-patched", ".java");
        Path report =
                Files.createTempFile("checker-reconcile-decrease-validation-report", ".jsonl");
        Files.write(
                plan,
                Arrays.asList(
                        captureStdout(
                                        "plan",
                                        "--source",
                                        source.toString(),
                                        "--trace",
                                        trace.toString())
                                .trim()
                                .split("\\R")),
                StandardCharsets.UTF_8);

        captureStdout(
                "apply-plan",
                "--source",
                source.toString(),
                "--plan",
                plan.toString(),
                "--out",
                patched.toString(),
                "--diagnostic",
                "E1",
                "--validate",
                "--validation-mode",
                "decrease",
                "--validation-report",
                report.toString(),
                "--javac",
                fakeJavacCountingLocalErrors().toString());

        TraceEvent reportEvent = new TraceParser().parse(report).get(0);
        assertEquals("validation_result", reportEvent.stringField("event"));
        assertEquals("decrease", reportEvent.stringField("validation_mode"));
        assertTrue(Boolean.parseBoolean(reportEvent.stringField("accepted")));
        assertEquals("3", reportEvent.stringField("applied_plan_count"));
        assertEquals("1", reportEvent.stringField("applied_edit_plan_count"));
        assertEquals("0", reportEvent.stringField("agent_origin_count"));
        assertEquals("2", reportEvent.stringField("before_diagnostic_count"));
        assertEquals("1", reportEvent.stringField("after_diagnostic_count"));
        assertTrue(reportEvent.listField("diagnostic_ids").contains("E1"));
    }

    @Test
    public void validationCanExportTraceDuringCompile() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = Files.createTempFile("checker-reconcile-validation-export", ".jsonl");
        Validation.Result result =
                new Validation(false)
                        .validateDetailed(fakeJavacWritingTrace().toString(), null, source, trace);

        assertEquals(1, result.exitCode());
        assertEquals(1, result.diagnosticCount());
        String traceText = new String(Files.readAllBytes(trace), StandardCharsets.UTF_8);
        assertTrue(traceText.contains("\"event\":\"diagnostic\""));
    }

    @Test
    public void lintPlanSummarizesAgentPlanRisk() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-lint-plan-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void f(String s) {",
                        "    @NonNull String t = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = text.indexOf("@NonNull");
        Path plan =
                writeTempJsonl(
                        "checker-reconcile-lint-plan",
                        "{\"schema_version\":1,\"diagnostic_id\":\"E1\","
                                + "\"kind\":\"CHANGE_QUALIFIER\",\"risk\":\"LOCAL_ONLY\","
                                + "\"automatic\":true,\"message\":\"deterministic\","
                                + "\"evidence_ids\":[\"A2\"],\"edits\":[{\"start_offset\":"
                                + annotationStart
                                + ",\"end_offset\":"
                                + (annotationStart + "@NonNull".length())
                                + ",\"replacement\":\"@Nullable\"}]}\n"
                                + "{\"schema_version\":1,\"origin\":\"agent\","
                                + "\"confidence\":0.6,\"requires_validation\":true,"
                                + "\"diagnostic_id\":\"E1\",\"kind\":\"REFACTOR\","
                                + "\"risk\":\"AGENT_ASSISTED\",\"automatic\":false,"
                                + "\"message\":\"agent sketch\","
                                + "\"evidence_ids\":[\"E1\"],\"edits\":[]}");

        String output =
                captureStdout(
                        "lint-plan", "--source", source.toString(), "--plan", plan.toString());

        assertTrue(output.contains("total: 2"));
        assertTrue(output.contains("origin:"));
        assertTrue(output.contains("  deterministic: 1"));
        assertTrue(output.contains("  agent: 1"));
        assertTrue(output.contains("  CHANGE_QUALIFIER: 1"));
        assertTrue(output.contains("  REFACTOR: 1"));
        assertTrue(output.contains("  LOCAL_ONLY: 1"));
        assertTrue(output.contains("  AGENT_ASSISTED: 1"));
        assertTrue(output.contains("edit-count: 1"));
        assertTrue(output.contains("requires-validation: 1"));
        assertTrue(output.contains("agent-automatic-edits: 0"));
    }

    @Test
    public void agentPlanConvertsProposalToValidatedPlan() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-agent-proposal-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void f(String s) {",
                        "    @NonNull String t = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = text.indexOf("@NonNull");
        Path proposal =
                writeTempJsonl(
                        "checker-reconcile-agent-proposal",
                        "{\"schema_version\":1,\"event\":\"agent_proposal\","
                                + "\"diagnostic_id\":\"E1\",\"kind\":\"CHANGE_QUALIFIER\","
                                + "\"risk\":\"LOCAL_ONLY\",\"automatic\":true,"
                                + "\"confidence\":0.8,\"requires_validation\":false,"
                                + "\"message\":\"agent proposed weakening\","
                                + "\"evidence_ids\":[\"A2\"],\"edits\":[{\"start_offset\":"
                                + annotationStart
                                + ",\"end_offset\":"
                                + (annotationStart + "@NonNull".length())
                                + ",\"replacement\":\"@Nullable\"}]}");

        String output =
                captureStdout(
                        "agent-plan",
                        "--source",
                        source.toString(),
                        "--proposal",
                        proposal.toString());
        TraceEvent plan =
                new TraceParser()
                        .parse(writeTempJsonl("checker-reconcile-agent-plan-output", output))
                        .get(0);

        assertEquals("1", plan.stringField("schema_version"));
        assertEquals("E1", plan.stringField("diagnostic_id"));
        assertEquals("agent", plan.stringField("origin"));
        assertEquals("0.8", plan.stringField("confidence"));
        assertTrue(Boolean.parseBoolean(plan.stringField("requires_validation")));
        assertEquals("CHANGE_QUALIFIER", plan.stringField("kind"));
        assertEquals(1, plan.listField("edits").size());
    }

    @Test
    public void agentPlanRejectsMalformedProposal() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-bad-agent-proposal-source", ".java");
        Files.write(source, Arrays.asList("class Example {}"), StandardCharsets.UTF_8);
        Path proposal =
                writeTempJsonl(
                        "checker-reconcile-bad-agent-proposal",
                        "{\"schema_version\":1,\"event\":\"agent_proposal\","
                                + "\"diagnostic_id\":\"E1\",\"kind\":\"BAD\","
                                + "\"risk\":\"LOCAL_ONLY\",\"automatic\":false,"
                                + "\"message\":\"bad\",\"evidence_ids\":[],\"edits\":[]}");

        try {
            captureStdout(
                    "agent-plan", "--source", source.toString(), "--proposal", proposal.toString());
            fail("expected malformed proposal rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("invalid kind BAD"));
        }
    }

    @Test
    public void agentPlanAcceptsProposalGroundedInContext() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        String sourceText = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = sourceText.indexOf("@NonNull");
        Path context = agentContextFor(source, trace);
        Path proposal =
                writeTempJsonl(
                        "checker-reconcile-grounded-agent-proposal",
                        agentProposalJson(
                                "E1",
                                "A2",
                                annotationStart,
                                annotationStart + "@NonNull".length()));

        String output =
                captureStdout(
                        "agent-plan",
                        "--context",
                        context.toString(),
                        "--proposal",
                        proposal.toString());
        TraceEvent plan =
                new TraceParser()
                        .parse(writeTempJsonl("checker-reconcile-grounded-agent-plan", output))
                        .get(0);

        assertEquals("agent", plan.stringField("origin"));
        assertEquals("E1", plan.stringField("diagnostic_id"));
        assertTrue(Boolean.parseBoolean(plan.stringField("requires_validation")));
        assertEquals(1, plan.listField("edits").size());
    }

    @Test
    public void agentPlanRejectsWrongDiagnosticForContext() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        String sourceText = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = sourceText.indexOf("@NonNull");
        Path context = agentContextFor(source, trace);
        Path proposal =
                writeTempJsonl(
                        "checker-reconcile-wrong-diagnostic-agent-proposal",
                        agentProposalJson(
                                "E2",
                                "A2",
                                annotationStart,
                                annotationStart + "@NonNull".length()));

        try {
            captureStdout(
                    "agent-plan",
                    "--context",
                    context.toString(),
                    "--proposal",
                    proposal.toString());
            fail("expected wrong diagnostic rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("does not match context E1"));
        }
    }

    @Test
    public void agentPlanRejectsUnknownEvidenceForContext() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        String sourceText = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = sourceText.indexOf("@NonNull");
        Path context = agentContextFor(source, trace);
        Path proposal =
                writeTempJsonl(
                        "checker-reconcile-unknown-evidence-agent-proposal",
                        agentProposalJson(
                                "E1",
                                "AX",
                                annotationStart,
                                annotationStart + "@NonNull".length()));

        try {
            captureStdout(
                    "agent-plan",
                    "--context",
                    context.toString(),
                    "--proposal",
                    proposal.toString());
            fail("expected unknown evidence rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unknown evidence_id AX"));
        }
    }

    @Test
    public void agentPlanRejectsOutOfRangeContextEdit() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        String sourceText = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        Path context = agentContextFor(source, trace);
        Path proposal =
                writeTempJsonl(
                        "checker-reconcile-out-of-range-agent-proposal",
                        agentProposalJson("E1", "A2", 0, sourceText.length() + 10));

        try {
            captureStdout(
                    "agent-plan",
                    "--context",
                    context.toString(),
                    "--proposal",
                    proposal.toString());
            fail("expected edit range rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("outside source length"));
        }
    }

    @Test
    public void cliAppliesApiChangePlanWhenRiskIsAllowed() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-cli-api-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  @NonNull String f;",
                        "  void assign(String s) {",
                        "    this.f = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = text.indexOf("@NonNull");
        Path trace = Files.createTempFile("checker-reconcile-cli-api-trace", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"field:f\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,\"source_target\":{\"kind\":\"field_annotation\",\"annotation\":\"@NonNull\",\"annotation_range\":{\"start_offset\":"
                                + annotationStart
                                + ",\"end_offset\":"
                                + (annotationStart + "@NonNull".length())
                                + "}}}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"field_assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        Path plan = Files.createTempFile("checker-reconcile-cli-api-plan", ".jsonl");
        Path patched = Files.createTempFile("checker-reconcile-cli-api-patched", ".java");

        String planOutput =
                captureStdout("plan", "--source", source.toString(), "--trace", trace.toString());
        Files.write(plan, Arrays.asList(planOutput.trim().split("\\R")), StandardCharsets.UTF_8);
        TraceEvent planEvent = new TraceParser().parse(plan).get(0);
        assertEquals("API_CHANGE", planEvent.stringField("risk"));

        captureStdout(
                "apply-plan",
                "--source",
                source.toString(),
                "--plan",
                plan.toString(),
                "--out",
                patched.toString(),
                "--allow-risk",
                "API_CHANGE");

        String patchedText = new String(Files.readAllBytes(patched), StandardCharsets.UTF_8);
        assertTrue(patchedText.contains("@Nullable String f;"));
    }

    @Test
    public void cliDiagnosePrintsRankedCandidates() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);

        String output = captureStdout("diagnose", trace.toString(), "--source", source.toString());

        assertTrue(output.contains("candidates:"));
        assertTrue(output.contains("CHANGE_QUALIFIER"));
        assertTrue(output.contains("assumptions=[A2]"));
        assertTrue(output.contains("cost=5"));
    }

    @Test
    public void cliSearchRepairAppliesFirstValidatedCandidate() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        Path patched = Files.createTempFile("checker-reconcile-search-patched", ".java");
        Path javac = fakeJavacAcceptingNullableLocal();

        String output =
                captureStdout(
                        "search-repair",
                        "--source",
                        source.toString(),
                        "--trace",
                        trace.toString(),
                        "--out",
                        patched.toString(),
                        "--validation-mode",
                        "pass",
                        "--allow-risk",
                        "LOCAL_ONLY",
                        "--javac",
                        javac.toString());

        String patchedText = new String(Files.readAllBytes(patched), StandardCharsets.UTF_8);
        assertTrue(output.contains("search accepted"));
        assertTrue(output.contains("candidate cost=5"));
        assertTrue(patchedText.contains("@Nullable String t = s;"));
    }

    @Test
    public void cliSearchRepairAcceptsDecreaseForOneDiagnosticGroup() throws Exception {
        Path source = sourceWithTwoExplicitLocals();
        Path trace = traceForTwoExplicitLocals(source);
        Path patched = Files.createTempFile("checker-reconcile-search-multi-patched", ".java");
        Path javac = fakeJavacCountingLocalErrors();

        String output =
                captureStdout(
                        "search-repair",
                        "--source",
                        source.toString(),
                        "--trace",
                        trace.toString(),
                        "--out",
                        patched.toString(),
                        "--validation-mode",
                        "decrease",
                        "--allow-risk",
                        "LOCAL_ONLY",
                        "--javac",
                        javac.toString());

        String patchedText = new String(Files.readAllBytes(patched), StandardCharsets.UTF_8);
        assertTrue(output.contains("search accepted"));
        assertTrue(output.contains("candidate cost=5"));
        assertTrue(patchedText.contains("@Nullable String t = s;"));
        assertTrue(patchedText.contains("@NonNull String u = s;"));
    }

    @Test
    public void cliSearchRepairUsesExportedTraceForSecondRound() throws Exception {
        Path source = sourceWithTwoExplicitLocals();
        Path trace = traceForExplicitLocal(source);
        Path patched = Files.createTempFile("checker-reconcile-search-iterative-patched", ".java");
        Path javac = fakeJavacExportingFollowupTrace();

        String output =
                captureStdout(
                        "search-repair",
                        "--source",
                        source.toString(),
                        "--trace",
                        trace.toString(),
                        "--out",
                        patched.toString(),
                        "--validation-mode",
                        "decrease",
                        "--allow-risk",
                        "LOCAL_ONLY",
                        "--search-rounds",
                        "2",
                        "--javac",
                        javac.toString());

        String patchedText = new String(Files.readAllBytes(patched), StandardCharsets.UTF_8);
        assertTrue(output.contains("search accepted"));
        assertTrue(output.contains("candidate cost=10"));
        assertTrue(patchedText.contains("@Nullable String t = s;"));
        assertTrue(patchedText.contains("@Nullable String u = s;"));
    }

    @Test
    public void cliSearchRepairAcceptsBoundedTwoEditCandidate() throws Exception {
        Path source = sourceWithTwoExplicitLocals();
        Path trace = traceForTwoExplicitLocals(source);
        Path patched = Files.createTempFile("checker-reconcile-search-two-edit-patched", ".java");
        Path javac = fakeJavacCountingLocalErrors();

        String output =
                captureStdout(
                        "search-repair",
                        "--source",
                        source.toString(),
                        "--trace",
                        trace.toString(),
                        "--out",
                        patched.toString(),
                        "--validation-mode",
                        "pass",
                        "--allow-risk",
                        "LOCAL_ONLY",
                        "--max-candidate-size",
                        "2",
                        "--javac",
                        javac.toString());

        String patchedText = new String(Files.readAllBytes(patched), StandardCharsets.UTF_8);
        assertTrue(output.contains("search accepted"));
        assertTrue(output.contains("candidate cost=10"));
        assertTrue(patchedText.contains("@Nullable String t = s;"));
        assertTrue(patchedText.contains("@Nullable String u = s;"));
    }

    @Test
    public void cliSearchRepairAcceptsBoundedThreeEditCandidate() throws Exception {
        Path source = sourceWithThreeExplicitLocals();
        Path trace = traceForThreeExplicitLocals(source);
        Path patched = Files.createTempFile("checker-reconcile-search-three-edit-patched", ".java");
        Path javac = fakeJavacCountingThreeLocalErrors();

        String output =
                captureStdout(
                        "search-repair",
                        "--source",
                        source.toString(),
                        "--trace",
                        trace.toString(),
                        "--out",
                        patched.toString(),
                        "--validation-mode",
                        "pass",
                        "--allow-risk",
                        "LOCAL_ONLY",
                        "--max-candidate-size",
                        "3",
                        "--max-search-candidates",
                        "7",
                        "--javac",
                        javac.toString());

        String patchedText = new String(Files.readAllBytes(patched), StandardCharsets.UTF_8);
        assertTrue(output.contains("search accepted"));
        assertTrue(output.contains("candidate cost=15"));
        assertTrue(patchedText.contains("@Nullable String t = s;"));
        assertTrue(patchedText.contains("@Nullable String u = s;"));
        assertTrue(patchedText.contains("@Nullable String v = s;"));
    }

    @Test
    public void validationBackedSearchRejectsInvalidBudgetBeforeJavac() throws Exception {
        Path source = sourceWithExplicitLocal();
        TraceModel model = loadModel(traceForExplicitLocal(source));
        Path out = Files.createTempFile("checker-reconcile-invalid-budget-out", ".java");

        try {
            new ValidationBackedRepairSearch()
                    .search(
                            source,
                            model,
                            out,
                            "missing-javac-for-budget-test",
                            null,
                            "pass",
                            RiskLevel.LOCAL_ONLY,
                            ValidationBackedRepairSearch.Listener.NOOP,
                            1,
                            0);
            fail("expected invalid search budget rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("maxSearchCandidates"));
        }
    }

    @Test
    public void validationBackedSearchReportsSkippedReceiverSketches() throws Exception {
        Path source = sourceWithReceiverDereference();
        TraceModel model = loadModel(traceForReceiverDereference(source));
        Path out = Files.createTempFile("checker-reconcile-skipped-receiver-out", ".java");

        ValidationBackedRepairSearch.Result result =
                new ValidationBackedRepairSearch()
                        .search(
                                source,
                                model,
                                out,
                                fakeJavacAcceptingNullableLocal().toString(),
                                null,
                                "decrease",
                                RiskLevel.BODY_CHANGE,
                                ValidationBackedRepairSearch.Listener.NOOP,
                                1,
                                10,
                                false);

        assertFalse(result.accepted());
        assertEquals(1, result.searchStats().generatedCandidateCount());
        assertEquals(1, result.searchStats().searchedCandidateCount());
        assertTrue(result.searchStats().skippedDiagnosticIds().contains("E1"));
        assertTrue(result.searchStats().uncoveredDiagnosticIds().contains("E1"));
        assertTrue(result.searchStats().rejectedDiagnosticIds().isEmpty());
    }

    @Test
    public void validationBackedSearchDoesNotInventRangeLessNullCheckFallback() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-range-less-assignment", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void f(Object[] o1) {",
                        "    o1[0] = null;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        Path trace = Files.createTempFile("checker-reconcile-range-less-assignment", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A1\",\"kind\":\"actual_qualifier\",\"slot\":\"expr:null\",\"type\":\"@Nullable NullType\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"target:o1[0]\",\"type\":\"@MonotonicNonNull Object\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable NullType\",\"want\":\"@MonotonicNonNull Object\",\"dependencies\":[\"A1\",\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        Path out = Files.createTempFile("checker-reconcile-range-less-out", ".java");

        ValidationBackedRepairSearch.Result result =
                new ValidationBackedRepairSearch()
                        .search(
                                source,
                                loadModel(trace),
                                out,
                                fakeJavacAcceptingNullableLocal().toString(),
                                null,
                                "decrease",
                                RiskLevel.BODY_CHANGE,
                                ValidationBackedRepairSearch.Listener.NOOP,
                                1,
                                10,
                                false);

        assertFalse(result.accepted());
        assertEquals(0, result.searchStats().generatedCandidateCount());
        assertEquals(0, result.searchStats().prunedEmptyEditCount());
    }

    @Test
    public void validationBackedSearchHandlesInferredArrayComponentWeakening() throws Exception {
        Path source = sourceWithMonotonicArrayComponent();
        TraceModel model = loadModel(traceForMonotonicArrayComponent(source));
        Path out = Files.createTempFile("checker-reconcile-monotonic-array-out", ".java");

        ValidationBackedRepairSearch.Result result =
                new ValidationBackedRepairSearch()
                        .search(
                                source,
                                model,
                                out,
                                fakeJavacAcceptingNullableArrayComponent().toString(),
                                null,
                                "pass",
                                RiskLevel.LOCAL_ONLY,
                                ValidationBackedRepairSearch.Listener.NOOP,
                                1,
                                10,
                                true);

        String patchedText = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
        assertTrue(result.accepted());
        assertTrue(result.searchStats().generatedCandidateCount() >= 1);
        assertTrue(patchedText.contains("@Nullable Object[] o1"));
        assertFalse(result.candidateSet().candidates().get(0).repair().automatic());
    }

    @Test
    public void validationBackedSearchReportsBudgetPruning() throws Exception {
        Path source = sourceWithThreeExplicitLocals();
        TraceModel model = loadModel(traceForThreeExplicitLocals(source));
        Path out = Files.createTempFile("checker-reconcile-budget-pruned-out", ".java");
        Path javac = fakeJavacCountingThreeLocalErrors();

        ValidationBackedRepairSearch.Result result =
                new ValidationBackedRepairSearch()
                        .search(
                                source,
                                model,
                                out,
                                javac.toString(),
                                null,
                                "pass",
                                RiskLevel.LOCAL_ONLY,
                                ValidationBackedRepairSearch.Listener.NOOP,
                                3,
                                6);

        assertFalse(result.accepted());
        assertEquals(7, result.searchStats().generatedCandidateCount());
        assertEquals(6, result.searchStats().searchedCandidateCount());
        assertEquals(1, result.searchStats().prunedBudgetCount());
        assertTrue(result.searchStats().allDiagnosticIds().contains("E1"));
        assertTrue(result.searchStats().allDiagnosticIds().contains("E2"));
        assertTrue(result.searchStats().allDiagnosticIds().contains("E3"));
        assertTrue(result.searchStats().validatedDiagnosticIds().contains("E1"));
        assertTrue(result.searchStats().validatedDiagnosticIds().contains("E2"));
        assertTrue(result.searchStats().validatedDiagnosticIds().contains("E3"));
        assertTrue(result.searchStats().acceptedDiagnosticIds().isEmpty());
        assertTrue(result.searchStats().rejectedDiagnosticIds().contains("E1"));
        assertTrue(result.searchStats().rejectedDiagnosticIds().contains("E2"));
        assertTrue(result.searchStats().rejectedDiagnosticIds().contains("E3"));
        assertTrue(result.searchStats().skippedDiagnosticIds().isEmpty());
        assertTrue(result.searchStats().uncoveredDiagnosticIds().isEmpty());
    }

    @Test
    public void validationBackedSearchCachesDuplicateEditValidation() throws Exception {
        Path source = sourceWithSharedField();
        TraceModel model = loadModel(traceForSharedFieldSourceTarget(source));
        Path javac = fakeJavacAcceptingNullableField();
        ValidationBackedRepairSearch.ValidationCache cache =
                new ValidationBackedRepairSearch.ValidationCache();
        ValidationBackedRepairSearch search =
                new ValidationBackedRepairSearch(
                        new DiagnosisEngine(), new Validation(false), cache);
        Set<String> firstDiagnostic = new LinkedHashSet<>();
        firstDiagnostic.add("E1");
        Set<String> secondDiagnostic = new LinkedHashSet<>();
        secondDiagnostic.add("E2");

        ValidationBackedRepairSearch.Result first =
                search.search(
                        source,
                        model,
                        Files.createTempFile("checker-reconcile-cache-first", ".java"),
                        javac.toString(),
                        null,
                        "decrease",
                        Set.of(RiskLevel.API_CHANGE),
                        firstDiagnostic,
                        ValidationBackedRepairSearch.Listener.NOOP,
                        1,
                        100,
                        false);
        ValidationBackedRepairSearch.Result second =
                search.search(
                        source,
                        model,
                        Files.createTempFile("checker-reconcile-cache-second", ".java"),
                        javac.toString(),
                        null,
                        "decrease",
                        Set.of(RiskLevel.API_CHANGE),
                        secondDiagnostic,
                        ValidationBackedRepairSearch.Listener.NOOP,
                        1,
                        100,
                        false);

        assertTrue(first.accepted());
        assertTrue(second.accepted());
        assertEquals(1, cache.missCount());
        assertEquals(1, cache.hitCount());
        assertEquals(1, cache.uniqueValidationCount());
    }

    @Test
    public void cliSearchRepairWritesStructuredSearchReport() throws Exception {
        Path source = sourceWithTwoExplicitLocals();
        Path trace = traceForTwoExplicitLocals(source);
        Path patched = Files.createTempFile("checker-reconcile-search-report-patched", ".java");
        Path report = Files.createTempFile("checker-reconcile-search-report", ".jsonl");
        Path javac = fakeJavacCountingLocalErrors();

        captureStdout(
                "search-repair",
                "--source",
                source.toString(),
                "--trace",
                trace.toString(),
                "--out",
                patched.toString(),
                "--validation-mode",
                "pass",
                "--allow-risk",
                "LOCAL_ONLY",
                "--max-candidate-size",
                "2",
                "--search-report",
                report.toString(),
                "--javac",
                javac.toString());

        List<TraceEvent> reportEvents = new SearchReportParser().parse(report);
        TraceEvent summary = reportEvents.get(reportEvents.size() - 1);
        assertEquals("summary", summary.stringField("event"));
        assertTrue(Boolean.TRUE.equals(summary.fields.get("accepted")));
        assertEquals(Long.valueOf(2), summary.fields.get("accepted_candidate_size"));
        assertEquals(Long.valueOf(0), summary.fields.get("after_diagnostic_count"));
        assertEquals(Long.valueOf(2), summary.fields.get("max_candidate_size"));
        assertEquals(Long.valueOf(100), summary.fields.get("max_search_candidates"));
        assertEquals(Long.valueOf(3), summary.fields.get("generated_candidate_count"));
        assertEquals(Long.valueOf(3), summary.fields.get("searched_candidate_count"));
        assertEquals(Long.valueOf(0), summary.fields.get("pruned_empty_edit_count"));
        assertTrue(((Map<?, ?>) summary.fields.get("pruned_empty_edit_reasons")).isEmpty());
        assertEquals(Long.valueOf(0), summary.fields.get("pruned_duplicate_edit_count"));
        assertEquals(Long.valueOf(0), summary.fields.get("pruned_overlap_count"));
        assertEquals(Long.valueOf(0), summary.fields.get("pruned_budget_count"));
        assertTrue(summary.listField("all_diagnostic_ids").contains("E1"));
        assertTrue(summary.listField("all_diagnostic_ids").contains("E2"));
        assertTrue(summary.listField("validated_diagnostic_ids").contains("E1"));
        assertTrue(summary.listField("validated_diagnostic_ids").contains("E2"));
        assertTrue(summary.listField("accepted_diagnostic_ids").contains("E1"));
        assertTrue(summary.listField("accepted_diagnostic_ids").contains("E2"));
        assertTrue(summary.listField("rejected_diagnostic_ids").isEmpty());
        assertTrue(summary.listField("skipped_diagnostic_ids").isEmpty());
        assertTrue(summary.listField("uncovered_diagnostic_ids").isEmpty());
        assertTrue(
                reportEvents.stream()
                        .anyMatch(
                                event ->
                                        event.stringField("event").equals("candidate_validated")
                                                && Boolean.FALSE.equals(
                                                        event.fields.get("accepted"))));
        assertTrue(
                reportEvents.stream()
                        .anyMatch(
                                event ->
                                        event.stringField("event").equals("candidate_validated")
                                                && Boolean.TRUE.equals(event.fields.get("accepted"))
                                                && Long.valueOf(2)
                                                        .equals(event.fields.get("candidate_size"))
                                                && event.listField("diagnostic_ids").contains("E1")
                                                && event.listField("diagnostic_ids")
                                                        .contains("E2")));

        String reportText = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);
        assertTrue(reportText.contains("\"repairs\""));
        assertTrue(reportText.contains("\"diagnostic_ids\""));
        assertTrue(reportText.contains("\"replacement\":\"@Nullable\""));
    }

    @Test
    public void searchRepairReportsBudgetPrunedCandidate() throws Exception {
        Path source = sourceWithThreeExplicitLocals();
        TraceModel model = loadModel(traceForThreeExplicitLocals(source));
        Path patched = Files.createTempFile("checker-reconcile-budget-report-patched", ".java");
        Path report = Files.createTempFile("checker-reconcile-budget-report", ".jsonl");
        Path javac = fakeJavacCountingThreeLocalErrors();
        List<String> reportLines = new ArrayList<>();
        SearchReportJson json = new SearchReportJson();
        ValidationBackedRepairSearch.Listener listener =
                new ValidationBackedRepairSearch.Listener() {
                    @Override
                    public void skipped(int index, RepairCandidateSet candidateSet, String reason) {
                        reportLines.add(json.skipped(index, candidateSet, reason));
                    }

                    @Override
                    public void validated(
                            int index,
                            RepairCandidateSet candidateSet,
                            Validation.Result after,
                            boolean accepted) {
                        reportLines.add(json.validated(index, candidateSet, after, accepted));
                    }

                    @Override
                    public void invalid(int index, RepairCandidateSet candidateSet, String reason) {
                        reportLines.add(json.invalid(index, candidateSet, reason));
                    }

                    @Override
                    public void pruned(int index, RepairCandidateSet candidateSet, String reason) {
                        reportLines.add(json.pruned(index, candidateSet, reason));
                    }
                };

        ValidationBackedRepairSearch.Result result =
                new ValidationBackedRepairSearch()
                        .search(
                                source,
                                model,
                                patched,
                                javac.toString(),
                                null,
                                "pass",
                                RiskLevel.LOCAL_ONLY,
                                listener,
                                3,
                                6);
        reportLines.add(json.summary(result));
        Files.write(report, reportLines, StandardCharsets.UTF_8);

        List<TraceEvent> reportEvents = new SearchReportParser().parse(report);
        TraceEvent summary = reportEvents.get(reportEvents.size() - 1);
        assertFalse(result.accepted());
        assertEquals(Long.valueOf(1), summary.fields.get("pruned_budget_count"));
        assertTrue(
                reportEvents.stream()
                        .anyMatch(
                                event ->
                                        event.stringField("event").equals("candidate_pruned")
                                                && event.stringField("reason").equals("budget")));
    }

    @Test
    public void searchSummaryExplainsPrunedSketchMaterializationFailures() throws Exception {
        Map<String, Integer> reasons = new java.util.LinkedHashMap<>();
        reasons.put("unsupported source_target kind: unknown_expression", 1);
        ValidationBackedRepairSearch.SearchStats stats =
                new ValidationBackedRepairSearch.SearchStats(
                        1,
                        10,
                        1,
                        0,
                        1,
                        0,
                        0,
                        0,
                        reasons,
                        Arrays.asList("E1"),
                        Arrays.<String>asList(),
                        Arrays.<String>asList(),
                        Arrays.<String>asList(),
                        Arrays.<String>asList());
        Validation.Result validationResult = new Validation.Result(1, 1);
        ValidationBackedRepairSearch.Result result =
                new ValidationBackedRepairSearch.Result(
                        false, validationResult, validationResult, null, stats);

        String summary = new checker_reconcile.diagnosis.SearchReportJson().summary(result);
        Path report = writeTempJsonl("checker-reconcile-pruned-sketch-report", summary);
        TraceEvent event = new SearchReportParser().parse(report).get(0);
        Map<?, ?> parsedReasons = (Map<?, ?>) event.fields.get("pruned_empty_edit_reasons");

        assertFalse(result.accepted());
        assertEquals(Long.valueOf(1), event.fields.get("pruned_empty_edit_count"));
        assertEquals(
                Long.valueOf(1),
                parsedReasons.get("unsupported source_target kind: unknown_expression"));
        assertTrue(summary.contains("\"pruned_empty_edit_reasons\""));
    }

    @Test
    public void searchReportSerializesPrunedSketchCandidate() throws Exception {
        SuggestedRepair repair =
                new SuggestedRepair(
                        RepairKind.ADD_NULL_CHECK,
                        Arrays.<SourceEdit>asList(),
                        RiskLevel.BODY_CHANGE,
                        false,
                        Arrays.asList("A1"),
                        "Sketch only in V0.",
                        Arrays.asList(
                                new RepairSketch(
                                        "add_null_check",
                                        "A1",
                                        false,
                                        "Insert a null check guarding b.",
                                        "unknown_expression",
                                        "b",
                                        42,
                                        43,
                                        "unsupported source_target kind: unknown_expression")));
        RepairCandidateSet candidateSet =
                new RepairCandidateSet(
                        Arrays.asList(
                                new RepairCandidate(
                                        repair,
                                        Arrays.asList("A1"),
                                        Arrays.asList("E1"),
                                        new RepairCost(55))));

        String json =
                new checker_reconcile.diagnosis.SearchReportJson()
                        .pruned(
                                1,
                                candidateSet,
                                "unsupported source_target kind: unknown_expression");
        TraceEvent event =
                new SearchReportParser()
                        .parse(writeTempJsonl("checker-reconcile-pruned-candidate-report", json))
                        .get(0);

        assertEquals("candidate_pruned", event.stringField("event"));
        assertEquals(
                "unsupported source_target kind: unknown_expression", event.stringField("reason"));
        assertTrue(event.listField("diagnostic_ids").contains("E1"));
        assertTrue(json.contains("\"sketches\""));
        assertTrue(
                json.contains(
                        "\"materialization_failure\":\"unsupported source_target kind: unknown_expression\""));
    }

    @Test
    public void cliAgentContextConsumesGeneratedSearchReport() throws Exception {
        Path source = sourceWithTwoExplicitLocals();
        Path trace = traceForTwoExplicitLocals(source);
        Path patched = Files.createTempFile("checker-reconcile-agent-report-patched", ".java");
        Path report = Files.createTempFile("checker-reconcile-agent-report", ".jsonl");
        Path javac = fakeJavacCountingLocalErrors();

        captureStdout(
                "search-repair",
                "--source",
                source.toString(),
                "--trace",
                trace.toString(),
                "--out",
                patched.toString(),
                "--validation-mode",
                "pass",
                "--allow-risk",
                "LOCAL_ONLY",
                "--max-candidate-size",
                "2",
                "--search-report",
                report.toString(),
                "--javac",
                javac.toString());

        String output =
                captureStdout(
                        "agent-context",
                        "--source",
                        source.toString(),
                        "--trace",
                        trace.toString(),
                        "--diagnostic",
                        "E1",
                        "--search-report",
                        report.toString());
        TraceEvent event =
                new AgentContextParser()
                        .parse(writeTempJsonl("checker-reconcile-generated-agent-context", output));

        assertEquals("agent_context", event.stringField("event"));
        assertEquals(4, event.listField("search_report").size());
        assertTrue(output.contains("\"search_summary\""));
        assertTrue(output.contains("\"accepted\":true"));
        assertTrue(output.contains("\"accepted_diagnostic_ids\":[\"E1\",\"E2\"]"));
        assertTrue(output.contains("\"rejected_diagnostic_ids\":[]"));
        assertTrue(output.contains("\"skipped_diagnostic_ids\":[]"));
        assertTrue(output.contains("\"uncovered_diagnostic_ids\":[]"));
    }

    @Test
    public void searchReportParserRejectsMalformedEntries() throws Exception {
        Path report =
                writeTempJsonl(
                        "checker-reconcile-bad-search-report",
                        "{\"schema_version\":1,\"event\":\"candidate_validated\","
                                + "\"candidate_index\":1,\"candidate_cost\":5,"
                                + "\"candidate_size\":1,\"diagnostic_ids\":[\"E1\"],"
                                + "\"accepted\":true,\"after_diagnostic_count\":0,"
                                + "\"after_exit_code\":0}");

        try {
            new SearchReportParser().parse(report);
            fail("expected malformed search report rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("non-empty repairs"));
        }
    }

    @Test
    public void cliSearchRepairContinuesAfterRejectedCandidate() throws Exception {
        Path source = sourceWithTwoExplicitLocals();
        Path trace = traceForTwoExplicitLocals(source);
        Path patched = Files.createTempFile("checker-reconcile-search-second-patched", ".java");
        Path javac = fakeJavacAcceptingNullableSecondLocal();

        String output =
                captureStdout(
                        "search-repair",
                        "--source",
                        source.toString(),
                        "--trace",
                        trace.toString(),
                        "--out",
                        patched.toString(),
                        "--validation-mode",
                        "pass",
                        "--allow-risk",
                        "LOCAL_ONLY",
                        "--javac",
                        javac.toString(),
                        "--explain-search");

        String patchedText = new String(Files.readAllBytes(patched), StandardCharsets.UTF_8);
        assertTrue(output.contains("candidate 1 cost=5"));
        assertTrue(output.contains("validation=rejected"));
        assertTrue(output.contains("candidate 2 cost=5"));
        assertTrue(output.contains("validation=accepted"));
        assertTrue(output.contains("search accepted"));
        assertTrue(output.contains("candidate cost=5"));
        assertTrue(patchedText.contains("@NonNull String t = s;"));
        assertTrue(patchedText.contains("@Nullable String u = s;"));
    }

    @Test
    public void cliRepairMatrixSummarizesFileBasedRepairMetadata() throws Exception {
        Path tests = Files.createTempDirectory("checker-reconcile-repair-matrix");
        Files.write(
                tests.resolve("LocalRepair.java"),
                Arrays.asList(
                        "// @repair-kind: assignment.type.incompatible",
                        "// @repair-mode: pass",
                        "// @repair-plan-kind: CHANGE_QUALIFIER",
                        "// @repair-plan-risk: LOCAL_ONLY",
                        "// @repair-plan-automatic: true",
                        "// @repair-plan-edits: 1",
                        "// @repair-runner: search",
                        "// @repair-search-mode: pass",
                        "// @repair-search-candidate-size: 2",
                        "// @repair-max-candidate-size: 2",
                        "// @repair-allow-risk: LOCAL_ONLY",
                        "class LocalRepair {}"),
                StandardCharsets.UTF_8);
        Files.write(
                tests.resolve("SketchRepair.java"),
                Arrays.asList(
                        "// @repair-kind: dereference.of.nullable",
                        "// @repair-mode: sketch",
                        "// @repair-plan-kind: ADD_NULL_CHECK",
                        "// @repair-plan-risk: BODY_CHANGE",
                        "// @repair-plan-automatic: false",
                        "// @repair-plan-edits: 0",
                        "class SketchRepair {}"),
                StandardCharsets.UTF_8);

        String output = captureStdout("repair-matrix", "--tests", tests.toString());

        assertTrue(output.contains("total: 2"));
        assertTrue(output.contains("diagnostic-kind:"));
        assertTrue(output.contains("  assignment.type.incompatible: 1"));
        assertTrue(output.contains("  dereference.of.nullable: 1"));
        assertTrue(output.contains("repair-mode:"));
        assertTrue(output.contains("  pass: 1"));
        assertTrue(output.contains("  sketch: 1"));
        assertTrue(output.contains("runner:"));
        assertTrue(output.contains("  search: 1"));
        assertTrue(output.contains("  patch: 1"));
        assertTrue(output.contains("search-mode:"));
        assertTrue(output.contains("  pass: 1"));
        assertTrue(output.contains("search-candidate-size:"));
        assertTrue(output.contains("  2: 1"));
        assertTrue(output.contains("max-candidate-size:"));
        assertTrue(output.contains("  2: 1"));
        assertTrue(output.contains("allow-risk:"));
        assertTrue(output.contains("  LOCAL_ONLY: 1"));
        assertTrue(output.contains("plan-kind:"));
        assertTrue(output.contains("  CHANGE_QUALIFIER: 1"));
        assertTrue(output.contains("  ADD_NULL_CHECK: 1"));
        assertTrue(output.contains("risk:"));
        assertTrue(output.contains("  LOCAL_ONLY: 1"));
        assertTrue(output.contains("  BODY_CHANGE: 1"));
        assertTrue(output.contains("automatic:"));
        assertTrue(output.contains("  true: 1"));
        assertTrue(output.contains("  false: 1"));
        assertTrue(output.contains("edit-count:"));
        assertTrue(output.contains("  1: 1"));
        assertTrue(output.contains("  0: 1"));
    }

    @Test
    public void corpusReportJsonRoundTripsAttemptAndSummary() throws Exception {
        Path root = Files.createTempDirectory("checker-reconcile-corpus-root");
        Path source = root.resolve("Example.java");
        Files.write(source, Arrays.asList("class Example {}"), StandardCharsets.UTF_8);
        Set<RiskLevel> risks = new LinkedHashSet<>();
        risks.add(RiskLevel.LOCAL_ONLY);
        risks.add(RiskLevel.API_CHANGE);
        CorpusCase corpusCase =
                new CorpusCase(
                        root,
                        source,
                        "Example.java",
                        "E1",
                        "assignment.type.incompatible",
                        Arrays.asList(RepairKind.CHANGE_QUALIFIER, RepairKind.ADD_NULL_CHECK),
                        Map.of("javac_options", "-AcheckPurityAnnotations", "search_rounds", "1"));
        ValidationBackedRepairSearch.SearchStats stats =
                new ValidationBackedRepairSearch.SearchStats(
                        3,
                        100,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0,
                        Map.<String, Integer>of(),
                        Arrays.asList("E1"),
                        Arrays.asList("E1"),
                        Arrays.asList("E1"),
                        Arrays.<String>asList(),
                        Arrays.<String>asList());
        CorpusAttempt attempt =
                new CorpusAttempt(
                        corpusCase,
                        true,
                        true,
                        true,
                        true,
                        false,
                        "",
                        "",
                        risks,
                        RepairKind.CHANGE_QUALIFIER,
                        RiskLevel.LOCAL_ONLY,
                        Arrays.asList(new CorpusEdit(10, 18, "@NonNull", "@Nullable")),
                        true,
                        false,
                        new Validation.Result(1, 2),
                        new Validation.Result(1, 1),
                        stats);
        CorpusSummary summary = new CorpusSummary(Arrays.asList(attempt));
        CorpusReportJson json = new CorpusReportJson();
        Path report =
                writeTempJsonl(
                        "checker-reconcile-corpus-report",
                        json.attempt(attempt) + System.lineSeparator() + json.summary(summary));

        List<TraceEvent> events = json.parse(report);
        TraceEvent attemptEvent = events.get(0);
        TraceEvent summaryEvent = events.get(1);

        assertEquals("corpus_attempt", attemptEvent.stringField("event"));
        assertEquals("Example.java", attemptEvent.stringField("source"));
        assertEquals("E1", attemptEvent.stringField("diagnostic_id"));
        assertEquals("assignment.type.incompatible", attemptEvent.stringField("diagnostic_kind"));
        assertEquals(source.toString(), attemptEvent.stringField("original_source"));
        assertEquals("", attemptEvent.stringField("trace_path"));
        assertEquals("", attemptEvent.stringField("work_source"));
        assertEquals("", attemptEvent.stringField("patched_source"));
        assertEquals(
                Arrays.asList("CHANGE_QUALIFIER", "ADD_NULL_CHECK"),
                attemptEvent.listField("possible_repair_kinds"));
        assertTrue(Boolean.TRUE.equals(attemptEvent.fields.get("accepted")));
        assertTrue(Boolean.TRUE.equals(attemptEvent.fields.get("decreased")));
        assertEquals("", attemptEvent.stringField("planner_reason"));
        assertTrue(Boolean.FALSE.equals(attemptEvent.fields.get("agent_refactor_target")));
        assertEquals("", attemptEvent.stringField("agent_refactor_context"));
        assertEquals(
                "-AcheckPurityAnnotations",
                ((Map<?, ?>) attemptEvent.fields.get("options")).get("javac_options"));
        assertEquals("1", ((Map<?, ?>) attemptEvent.fields.get("options")).get("search_rounds"));
        List<Object> acceptedEdits = attemptEvent.listField("accepted_edits");
        assertEquals(1, acceptedEdits.size());
        assertEquals("@NonNull", ((Map<?, ?>) acceptedEdits.get(0)).get("original"));
        assertEquals("@Nullable", ((Map<?, ?>) acceptedEdits.get(0)).get("replacement"));
        assertTrue(Boolean.TRUE.equals(attemptEvent.fields.get("validation_cache_hit")));
        assertTrue(Boolean.FALSE.equals(attemptEvent.fields.get("validation_cache_miss")));
        assertTrue(attemptEvent.listField("allowed_risks").contains("LOCAL_ONLY"));
        assertTrue(attemptEvent.listField("allowed_risks").contains("API_CHANGE"));
        assertEquals(Long.valueOf(1), attemptEvent.fields.get("after_diagnostic_count"));
        assertEquals("corpus_summary", summaryEvent.stringField("event"));
        assertEquals(Long.valueOf(1), summaryEvent.fields.get("total"));
        assertEquals(Long.valueOf(1), summaryEvent.fields.get("validation_cache_hits"));
        assertEquals(Long.valueOf(0), summaryEvent.fields.get("unique_validated_patches"));
        assertEquals(
                Long.valueOf(1),
                ((Map<?, ?>) summaryEvent.fields.get("by_possible_repair_kind"))
                        .get("CHANGE_QUALIFIER"));
        assertEquals(
                Long.valueOf(1),
                ((Map<?, ?>) summaryEvent.fields.get("by_possible_repair_kind"))
                        .get("ADD_NULL_CHECK"));
        assertEquals(
                Long.valueOf(1),
                ((Map<?, ?>) summaryEvent.fields.get("by_possible_repair_kind_outcome"))
                        .get("CHANGE_QUALIFIER/accepted"));
        assertEquals(
                Long.valueOf(1),
                ((Map<?, ?>) summaryEvent.fields.get("by_possible_repair_kind_outcome"))
                        .get("ADD_NULL_CHECK/different repair accepted"));
        assertEquals(
                "Example.java#E1",
                ((Map<?, ?>) summaryEvent.fields.get("by_possible_repair_kind_outcome_example"))
                        .get("CHANGE_QUALIFIER/accepted"));
        assertEquals(
                "Example.java#E1",
                ((Map<?, ?>) summaryEvent.fields.get("by_outcome_example"))
                        .get("decreased_not_full_pass"));
        assertTrue(((Map<?, ?>) summaryEvent.fields.get("by_planner_reason")).isEmpty());
        assertTrue(((Map<?, ?>) summaryEvent.fields.get("by_agent_refactor_context")).isEmpty());
        assertTrue(
                ((Map<?, ?>) summaryEvent.fields.get("by_agent_refactor_context_example"))
                        .isEmpty());
        assertTrue(
                ((Map<?, ?>) summaryEvent.fields.get("by_accepted_edit"))
                        .containsKey("Example.java @NonNull -> @Nullable @ 10:18"));
        assertTrue(summary.render().contains("accepted: 1"));
        assertTrue(summary.render().contains("full-pass: 0"));
        assertTrue(summary.render().contains("possible-repair-kind:"));
        assertTrue(summary.render().contains("possible-repair-kind-outcome:"));
        assertTrue(summary.render().contains("possible-repair-kind-outcome-example:"));
        assertTrue(summary.render().contains("outcome-example:"));
    }

    @Test
    public void corpusReportSummarizesAgentRefactorTargets() throws Exception {
        CorpusCase corpusCase =
                new CorpusCase(
                        Path.of("/tmp"),
                        Path.of("/tmp/Example.java"),
                        "Example.java",
                        "E1",
                        "return.type.incompatible",
                        Arrays.asList(RepairKind.ADD_NULL_CHECK),
                        Map.<String, String>of());
        CorpusAttempt attempt =
                new CorpusAttempt(
                        corpusCase,
                        true,
                        true,
                        false,
                        false,
                        false,
                        "",
                        "return expression",
                        new LinkedHashSet<RiskLevel>(),
                        null,
                        null,
                        Arrays.<CorpusEdit>asList(),
                        false,
                        false,
                        new Validation.Result(1, 1),
                        new Validation.Result(1, 1),
                        null);
        CorpusSummary summary = new CorpusSummary(Arrays.asList(attempt));
        CorpusReportJson json = new CorpusReportJson();
        Path report =
                writeTempJsonl(
                        "checker-reconcile-corpus-agent-refactor",
                        json.attempt(attempt) + System.lineSeparator() + json.summary(summary));

        List<TraceEvent> events = json.parse(report);
        TraceEvent attemptEvent = events.get(0);
        TraceEvent summaryEvent = events.get(1);

        assertTrue(Boolean.TRUE.equals(attemptEvent.fields.get("agent_refactor_target")));
        assertEquals("return_expression", attemptEvent.stringField("agent_refactor_context"));
        assertEquals(
                Long.valueOf(1),
                ((Map<?, ?>) summaryEvent.fields.get("by_agent_refactor_context"))
                        .get("return_expression"));
        assertEquals(
                "Example.java#E1",
                ((Map<?, ?>) summaryEvent.fields.get("by_agent_refactor_context_example"))
                        .get("return_expression"));
        assertEquals(
                "Example.java#E1",
                ((Map<?, ?>) summaryEvent.fields.get("by_outcome_example"))
                        .get("agent_refactor_target"));
        assertEquals(Integer.valueOf(1), summary.byAgentRefactorContext().get("return_expression"));
        assertEquals(
                "Example.java#E1",
                summary.byAgentRefactorContextExample().get("return_expression"));
        assertTrue(summary.render().contains("agent-refactor-context:"));
        assertTrue(summary.render().contains("return_expression: 1"));
        assertTrue(summary.render().contains("agent-refactor-context-example:"));
    }

    @Test
    public void cliCorpusInspectPrintsActionableBuckets() throws Exception {
        String report =
                corpusAttemptJson(
                                "Accepted.java",
                                "E1",
                                "assignment.type.incompatible",
                                true,
                                false,
                                false,
                                "",
                                "",
                                "",
                                "CHANGE_QUALIFIER",
                                "LOCAL_ONLY",
                                2,
                                1)
                        + System.lineSeparator()
                        + corpusAttemptJson(
                                "Agent.java",
                                "E2",
                                "argument.type.incompatible",
                                false,
                                false,
                                true,
                                "all candidates pruned",
                                "only sketch or unmaterialized repairs: return expression",
                                "return_expression",
                                "",
                                "",
                                1,
                                1)
                        + System.lineSeparator()
                        + corpusAttemptJson(
                                "Rejected.java",
                                "E3",
                                "return.type.incompatible",
                                false,
                                false,
                                false,
                                "validation rejected",
                                "candidate repairs failed validation",
                                "",
                                "",
                                "",
                                1,
                                1)
                        + System.lineSeparator()
                        + corpusAttemptJson(
                                "Pass.java",
                                "E4",
                                "dereference.of.nullable",
                                true,
                                true,
                                false,
                                "",
                                "",
                                "",
                                "ADD_NULL_CHECK",
                                "BODY_CHANGE",
                                1,
                                0);
        Path reportPath = writeTempJsonl("checker-reconcile-corpus-inspect", report);

        String output =
                captureStdout("corpus-inspect", "--report", reportPath.toString(), "--limit", "1");

        assertTrue(output.contains("attempts: 4"));
        assertTrue(output.contains("accepted-not-full-pass:"));
        assertTrue(output.contains("Accepted.java#E1"));
        assertTrue(output.contains("agent-refactor-target:"));
        assertTrue(output.contains("Agent.java#E2"));
        assertTrue(output.contains("agent-context: return_expression"));
        assertTrue(output.contains("validation-rejected:"));
        assertTrue(output.contains("Rejected.java#E3"));
        assertTrue(output.contains("unsupported-or-no-edit:"));
        assertTrue(output.contains("full-pass:"));
        assertTrue(output.contains("Pass.java#E4"));
        assertTrue(output.contains("patched: /work/Accepted.java"));
    }

    @Test
    public void cfTestDiagnosticMinerClassifiesNullnessContextDiagnostics() throws Exception {
        Path root = Files.createTempDirectory("checker-reconcile-corpus-mine");
        Path file = root.resolve("Examples.java");
        Files.write(
                file,
                Arrays.asList(
                        "class Examples {",
                        "  void f(Object o, Boolean b) {",
                        "    // :: error: (dereference.of.nullable)",
                        "    o.toString();",
                        "    // :: error: (condition.nullable)",
                        "    if (b) {}",
                        "    // :: error: (unboxing.of.nullable)",
                        "    boolean value = b;",
                        "    // :: error: (accessing.nullable)",
                        "    o.hashCode();",
                        "    // :: error: (iterating.over.nullable)",
                        "    for (Object x : (Iterable<Object>) o) {}",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);

        Map<String, Integer> counts =
                new CfTestDiagnosticMiner().countsByKind(new CfTestDiagnosticMiner().mine(root));

        assertEquals(Integer.valueOf(1), counts.get("dereference.of.nullable"));
        assertEquals(Integer.valueOf(1), counts.get("condition.nullable"));
        assertEquals(Integer.valueOf(1), counts.get("unboxing.of.nullable"));
        assertEquals(Integer.valueOf(1), counts.get("accessing.nullable"));
        assertEquals(Integer.valueOf(1), counts.get("iterating.over.nullable"));
    }

    @Test
    public void cfTestDiagnosticMinerSkipsDisabledCfTests() throws Exception {
        Path root = Files.createTempDirectory("checker-reconcile-corpus-skip");
        Path skipped = root.resolve("Skipped.java");
        Path enabled = root.resolve("Enabled.java");
        Files.write(
                skipped,
                Arrays.asList(
                        "// @skip-test",
                        "class Skipped {",
                        "  void f(Object o) {",
                        "    // :: error: (dereference.of.nullable)",
                        "    o.toString();",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        Files.write(
                enabled,
                Arrays.asList(
                        "class Enabled {",
                        "  void f(Object o) {",
                        "    // :: error: (dereference.of.nullable)",
                        "    o.toString();",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);

        List<Candidate> candidates = new CfTestDiagnosticMiner().mine(root);

        assertFalse(CfTestMetadata.isJavaTestFile(skipped));
        assertTrue(CfTestMetadata.isJavaTestFile(enabled));
        assertEquals(1, candidates.size());
        assertEquals("Enabled.java", candidates.get(0).file());
    }

    @Test
    public void validationBackedSearchCanFilterDiagnosticAndAllowMultipleRisks() throws Exception {
        Path source = sourceWithThreeExplicitLocals();
        TraceModel model = loadModel(traceForThreeExplicitLocals(source));
        Path out = Files.createTempFile("checker-reconcile-diagnostic-filter-out", ".java");
        Path javac = fakeJavacCountingThreeLocalErrors();
        Set<RiskLevel> risks = new LinkedHashSet<>();
        risks.add(RiskLevel.LOCAL_ONLY);
        risks.add(RiskLevel.API_CHANGE);
        Set<String> diagnostics = new LinkedHashSet<>();
        diagnostics.add("E1");

        ValidationBackedRepairSearch.Result result =
                new ValidationBackedRepairSearch()
                        .search(
                                source,
                                model,
                                out,
                                javac.toString(),
                                null,
                                "decrease",
                                risks,
                                diagnostics,
                                ValidationBackedRepairSearch.Listener.NOOP,
                                3,
                                100,
                                false);

        String patchedText = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
        assertTrue(result.accepted());
        assertEquals(Arrays.asList("E1"), result.searchStats().allDiagnosticIds());
        assertEquals(Arrays.asList("E1"), result.searchStats().acceptedDiagnosticIds());
        assertTrue(patchedText.contains("@Nullable String t = s;"));
        assertTrue(patchedText.contains("@NonNull String u = s;"));
        assertTrue(patchedText.contains("@NonNull String v = s;"));
    }

    @Test
    public void cliApplyPlanAcceptsCommaSeparatedRiskFilters() throws Exception {
        Path source = sourceWithExplicitLocal();
        Path trace = traceForExplicitLocal(source);
        Path plan = Files.createTempFile("checker-reconcile-risk-filter-plan", ".jsonl");
        Path out = Files.createTempFile("checker-reconcile-risk-filter-out", ".java");
        String planOutput =
                captureStdout("plan", "--source", source.toString(), "--trace", trace.toString());
        Files.write(plan, planOutput.getBytes(StandardCharsets.UTF_8));

        captureStdout(
                "apply-plan",
                "--source",
                source.toString(),
                "--plan",
                plan.toString(),
                "--out",
                out.toString(),
                "--allow-risk",
                "LOCAL_ONLY,API_CHANGE");

        String patchedText = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
        assertTrue(patchedText.contains("@Nullable String t = s;"));
    }

    @Test
    public void applyPlanRejectsMalformedSchemaEntries() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-schema-source", ".java");
        Files.write(source, Arrays.asList("class Example {}"), StandardCharsets.UTF_8);
        Path out = Files.createTempFile("checker-reconcile-schema-out", ".java");

        assertPlanRejected(
                source,
                out,
                "{\"diagnostic_id\":\"E1\",\"kind\":\"CHANGE_QUALIFIER\",\"risk\":\"LOCAL_ONLY\",\"automatic\":true,\"message\":\"bad\",\"evidence_ids\":[],\"edits\":[]}",
                "schema_version 1");
        assertPlanRejected(
                source,
                out,
                "{\"schema_version\":1,\"diagnostic_id\":\"E1\",\"risk\":\"LOCAL_ONLY\",\"automatic\":true,\"message\":\"bad\",\"evidence_ids\":[],\"edits\":[]}",
                "missing kind");
        assertPlanRejected(
                source,
                out,
                "{\"schema_version\":1,\"diagnostic_id\":\"E1\",\"kind\":\"BAD\",\"risk\":\"LOCAL_ONLY\",\"automatic\":true,\"message\":\"bad\",\"evidence_ids\":[],\"edits\":[]}",
                "invalid kind");
        assertPlanRejected(
                source,
                out,
                "{\"schema_version\":1,\"diagnostic_id\":\"E1\",\"kind\":\"CHANGE_QUALIFIER\",\"risk\":\"BAD\",\"automatic\":true,\"message\":\"bad\",\"evidence_ids\":[],\"edits\":[]}",
                "invalid risk");
        assertPlanRejected(
                source,
                out,
                "{\"schema_version\":1,\"diagnostic_id\":\"E1\",\"kind\":\"CHANGE_QUALIFIER\",\"risk\":\"LOCAL_ONLY\",\"automatic\":true,\"message\":\"bad\",\"evidence_ids\":[],\"edits\":[{\"start_offset\":\"bad\",\"end_offset\":1,\"replacement\":\"x\"}]}",
                "missing numeric start_offset");
        assertPlanRejected(
                source,
                out,
                "{\"schema_version\":1,\"origin\":\"agent\",\"confidence\":2.0,\"diagnostic_id\":\"E1\",\"kind\":\"CHANGE_QUALIFIER\",\"risk\":\"LOCAL_ONLY\",\"automatic\":true,\"message\":\"bad\",\"evidence_ids\":[],\"edits\":[]}",
                "confidence must be between 0 and 1");
        assertPlanRejected(
                source,
                out,
                "{\"schema_version\":1,\"origin\":\"agent\",\"requires_validation\":\"yes\",\"diagnostic_id\":\"E1\",\"kind\":\"CHANGE_QUALIFIER\",\"risk\":\"LOCAL_ONLY\",\"automatic\":true,\"message\":\"bad\",\"evidence_ids\":[],\"edits\":[]}",
                "requires_validation must be boolean");
    }

    @Test
    public void doesNotApplySuppressionSketch() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {", "  void f(String s) {", "    String t = s;", "  }", "}"),
                StandardCharsets.UTF_8);
        Path trace = Files.createTempFile("checker-reconcile-no-suppression", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));
        TraceModel.DiagnosticSlice slice = model.slice("E1");
        Path patched = Files.createTempFile("checker-reconcile-no-suppression-patched", ".java");

        new Patcher().writePatched(source, patched, slice);

        String patchedText = new String(Files.readAllBytes(patched), StandardCharsets.UTF_8);
        assertFalse(patchedText.contains("@SuppressWarnings"));
        assertTrue(patchedText.contains("String t = s;"));
    }

    @Test
    public void appliesSourceEditsInDescendingOffsetOrder() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-edit-source", ".java");
        Files.write(source, Arrays.asList("class Example {}"), StandardCharsets.UTF_8);
        Path patched = Files.createTempFile("checker-reconcile-edit-patched", ".java");
        String original = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int classStart = original.indexOf("class");
        int nameStart = original.indexOf("Example");

        new PatchApplier()
                .writePatched(
                        source,
                        patched,
                        Arrays.asList(
                                new SourceEdit(
                                        source,
                                        classStart,
                                        classStart + "class".length(),
                                        "final class"),
                                new SourceEdit(
                                        source,
                                        nameStart,
                                        nameStart + "Example".length(),
                                        "Demo")));

        String patchedText = new String(Files.readAllBytes(patched), StandardCharsets.UTF_8);
        assertTrue(patchedText.contains("final class Demo"));
    }

    @Test
    public void patcherAppliesMultipleAutomaticRepairs() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-multi-source", ".java");
        Files.write(source, Arrays.asList("class Example {}"), StandardCharsets.UTF_8);
        String original = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int classStart = original.indexOf("class");
        int nameStart = original.indexOf("Example");
        Path patched = Files.createTempFile("checker-reconcile-multi-patched", ".java");

        new Patcher()
                .writePlanned(
                        source,
                        patched,
                        Arrays.asList(
                                repair(
                                        new SourceEdit(
                                                source,
                                                classStart,
                                                classStart + "class".length(),
                                                "final class")),
                                repair(
                                        new SourceEdit(
                                                source,
                                                nameStart,
                                                nameStart + "Example".length(),
                                                "Demo"))));

        String patchedText = new String(Files.readAllBytes(patched), StandardCharsets.UTF_8);
        assertTrue(patchedText.contains("final class Demo"));
    }

    @Test
    public void patcherRejectsOverlappingSelectedRepairsBeforeWriting() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-overlap-source", ".java");
        Files.write(source, Arrays.asList("class Example {}"), StandardCharsets.UTF_8);
        String original = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int nameStart = original.indexOf("Example");
        Path patched =
                Files.createTempDirectory("checker-reconcile-overlap-patched").resolve("Out.java");

        try {
            new Patcher()
                    .writePlanned(
                            source,
                            patched,
                            Arrays.asList(
                                    repair(
                                            new SourceEdit(
                                                    source,
                                                    nameStart,
                                                    nameStart + "Example".length(),
                                                    "Demo")),
                                    repair(
                                            new SourceEdit(
                                                    source,
                                                    nameStart + 1,
                                                    nameStart + "Example".length(),
                                                    "Short"))));
            fail("expected overlapping source edit failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("overlap"));
        }
        assertFalse(Files.exists(patched));
    }

    @Test
    public void appendsAgentAssistedSketchesAfterDeterministicRepairs() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-agent-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void f(String s) {",
                        "    @NonNull String t = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        Path trace = Files.createTempFile("checker-reconcile-agent", ".jsonl");
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = text.indexOf("@NonNull");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"local:t\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,\"source_target\":{\"kind\":\"local_annotation\",\"annotation\":\"@NonNull\",\"annotation_range\":{\"start_offset\":"
                                + annotationStart
                                + ",\"end_offset\":"
                                + (annotationStart + "@NonNull".length())
                                + "}}}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        TraceModel.DiagnosticSlice slice =
                TraceModel.fromEvents(new TraceParser().parse(trace)).slice("E1");

        List<SuggestedRepair> repairs =
                new RepairPlanner(TraceParserTest::agentAdvice).plan(source, slice);

        assertEquals(RepairKind.CHANGE_QUALIFIER, repairs.get(0).kind());
        assertEquals(RepairKind.REFACTOR, repairs.get(repairs.size() - 1).kind());
        assertFalse(repairs.get(repairs.size() - 1).automatic());
    }

    @Test
    public void plannerAcceptsInjectedDeterministicRules() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-rule-source", ".java");
        Files.write(source, Arrays.asList("class Example {}"), StandardCharsets.UTF_8);
        Path trace = Files.createTempFile("checker-reconcile-rule", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        TraceModel.DiagnosticSlice slice =
                TraceModel.fromEvents(new TraceParser().parse(trace)).slice("E1");

        List<SuggestedRepair> repairs =
                new RepairPlanner(
                                Arrays.<RepairRule>asList(TraceParserTest::customRule),
                                new NoopAgentRepairAdvisor())
                        .plan(source, slice);

        assertEquals(1, repairs.size());
        assertEquals(RepairKind.REFACTOR, repairs.get(0).kind());
        assertEquals(RiskLevel.UNKNOWN, repairs.get(0).risk());
    }

    private static List<SuggestedRepair> customRule(Path source, TraceModel.DiagnosticSlice slice) {
        return Arrays.asList(
                new SuggestedRepair(
                        RepairKind.REFACTOR,
                        Arrays.<SourceEdit>asList(),
                        RiskLevel.UNKNOWN,
                        false,
                        Arrays.asList(slice.diagnostic().id),
                        "Injected rule sketch."));
    }

    private static SuggestedRepair repair(SourceEdit edit) {
        return new SuggestedRepair(
                RepairKind.REFACTOR,
                Arrays.asList(edit),
                RiskLevel.LOCAL_ONLY,
                true,
                Arrays.asList("E1"),
                "test repair");
    }

    private static SuggestedRepair repair(String message, String evidenceId, SourceEdit edit) {
        return new SuggestedRepair(
                RepairKind.CHANGE_QUALIFIER,
                Arrays.asList(edit),
                RiskLevel.LOCAL_ONLY,
                true,
                Arrays.asList(evidenceId),
                message);
    }

    private static SuggestedRepair nullCheckRepair(
            String sourceTargetKind, String expression, int startOffset, int endOffset) {
        return nullCheckRepair(
                sourceTargetKind,
                expression,
                startOffset,
                endOffset,
                java.util.Collections.emptyMap());
    }

    private static SuggestedRepair nullCheckRepair(
            String sourceTargetKind,
            String expression,
            int startOffset,
            int endOffset,
            Map<String, Object> sourceTargetAttributes) {
        return new SuggestedRepair(
                RepairKind.ADD_NULL_CHECK,
                Arrays.<SourceEdit>asList(),
                RiskLevel.BODY_CHANGE,
                false,
                Arrays.asList("O1"),
                "Sketch only in V0.",
                Arrays.asList(
                        new RepairSketch(
                                "add_null_check",
                                "A1",
                                false,
                                "Insert a null check guarding " + expression + ".",
                                sourceTargetKind,
                                expression,
                                startOffset,
                                endOffset,
                                sourceTargetAttributes,
                                "")));
    }

    private static RepairCandidateSet candidateSet(SuggestedRepair repair, int cost) {
        return new RepairCandidateSet(
                Arrays.asList(
                        new RepairCandidate(
                                repair,
                                repair.evidenceIds(),
                                Arrays.asList("E1"),
                                new RepairCost(cost))));
    }

    private static boolean hasCandidateSetWithMessages(
            List<RepairCandidateSet> candidateSets, String first, String second) {
        for (RepairCandidateSet candidateSet : candidateSets) {
            boolean hasFirst = false;
            boolean hasSecond = false;
            for (checker_reconcile.diagnosis.RepairCandidate candidate :
                    candidateSet.candidates()) {
                hasFirst = hasFirst || candidate.message().equals(first);
                hasSecond = hasSecond || candidate.message().equals(second);
            }
            if (hasFirst && hasSecond) {
                return true;
            }
        }
        return false;
    }

    private static List<SuggestedRepair> agentAdvice(AgentRepairRequest request) {
        assertFalse(request.deterministicRepairs().isEmpty());
        return Arrays.asList(
                new SuggestedRepair(
                        RepairKind.REFACTOR,
                        Arrays.<SourceEdit>asList(),
                        RiskLevel.AGENT_ASSISTED,
                        false,
                        Arrays.asList(request.slice().diagnostic().id),
                        "Agent-assisted refactor sketch; validation required before use."));
    }

    private static Path agentContextFor(Path source, Path trace) throws Exception {
        return writeTempJsonl(
                "checker-reconcile-grounding-agent-context",
                captureStdout(
                        "agent-context",
                        "--source",
                        source.toString(),
                        "--trace",
                        trace.toString(),
                        "--diagnostic",
                        "E1"));
    }

    private static String agentProposalJson(
            String diagnosticId, String evidenceId, int startOffset, int endOffset) {
        return "{\"schema_version\":1,\"event\":\"agent_proposal\","
                + "\"diagnostic_id\":\""
                + diagnosticId
                + "\",\"kind\":\"CHANGE_QUALIFIER\","
                + "\"risk\":\"LOCAL_ONLY\",\"automatic\":true,"
                + "\"confidence\":0.8,\"requires_validation\":false,"
                + "\"message\":\"agent proposed weakening\","
                + "\"evidence_ids\":[\""
                + evidenceId
                + "\"],\"edits\":[{\"start_offset\":"
                + startOffset
                + ",\"end_offset\":"
                + endOffset
                + ",\"replacement\":\"@Nullable\"}]}";
    }

    private static String corpusAttemptJson(
            String source,
            String diagnosticId,
            String diagnosticKind,
            boolean accepted,
            boolean fullPass,
            boolean agentRefactorTarget,
            String failureReason,
            String plannerReason,
            String agentRefactorContext,
            String acceptedRepairKind,
            String acceptedRisk,
            int beforeDiagnosticCount,
            int afterDiagnosticCount) {
        return "{\"schema_version\":1,\"event\":\"corpus_attempt\","
                + "\"source\":\""
                + source
                + "\",\"diagnostic_id\":\""
                + diagnosticId
                + "\",\"diagnostic_kind\":\""
                + diagnosticKind
                + "\",\"original_source\":\"/original/"
                + source
                + "\",\"trace_path\":\"/work/trace.jsonl\","
                + "\"work_source\":\"/work/"
                + source
                + "\",\"patched_source\":\"/work/"
                + source
                + "\",\"possible_repair_kinds\":[],"
                + "\"trace_ok\":true,\"search_ok\":true,"
                + "\"accepted\":"
                + accepted
                + ",\"decreased\":"
                + (afterDiagnosticCount < beforeDiagnosticCount)
                + ",\"full_pass\":"
                + fullPass
                + ",\"failure_reason\":\""
                + failureReason
                + "\",\"planner_reason\":\""
                + plannerReason
                + "\",\"agent_refactor_target\":"
                + agentRefactorTarget
                + ",\"agent_refactor_context\":\""
                + agentRefactorContext
                + "\",\"options\":{},\"allowed_risks\":[],"
                + "\"accepted_risk\":\""
                + acceptedRisk
                + "\",\"accepted_repair_kind\":\""
                + acceptedRepairKind
                + "\",\"accepted_edits\":[],"
                + "\"validation_cache_hit\":false,\"validation_cache_miss\":false,"
                + "\"before_diagnostic_count\":"
                + beforeDiagnosticCount
                + ",\"before_exit_code\":1,\"after_diagnostic_count\":"
                + afterDiagnosticCount
                + ",\"after_exit_code\":"
                + (fullPass ? 0 : 1)
                + ",\"generated_candidate_count\":0,\"searched_candidate_count\":0,"
                + "\"pruned_empty_edit_count\":0,\"pruned_duplicate_edit_count\":0,"
                + "\"pruned_overlap_count\":0,\"pruned_budget_count\":0}";
    }

    private static String captureStdout(String... args) throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            Cli.main(args);
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static void assertPlanRejected(
            Path source, Path out, String planJson, String expectedMessage) throws Exception {
        Path plan = writeTempJsonl("checker-reconcile-malformed-plan", planJson);
        try {
            captureStdout(
                    "apply-plan",
                    "--source",
                    source.toString(),
                    "--plan",
                    plan.toString(),
                    "--out",
                    out.toString());
            fail("expected malformed plan rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains(expectedMessage));
        }
    }

    private static Path writeTempJsonl(String prefix, String text) throws Exception {
        Path path = Files.createTempFile(prefix, ".jsonl");
        Files.write(path, Arrays.asList(text.trim().split("\\R")), StandardCharsets.UTF_8);
        return path;
    }

    private static TraceModel loadModel(Path trace) throws Exception {
        return TraceModel.fromEvents(new TraceParser().parse(trace));
    }

    private static Path sourceWithExplicitLocal() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.NonNull;",
                        "class Example {",
                        "  void f(String s) {",
                        "    @NonNull String t = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        return source;
    }

    private static Path traceForExplicitLocal(Path source) throws Exception {
        Path trace = Files.createTempFile("checker-reconcile-patch", ".jsonl");
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = text.indexOf("@NonNull");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A1\",\"kind\":\"actual_qualifier\",\"slot\":\"expr:s\",\"type\":\"@Nullable String\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"local:t\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,\"source_target\":{\"kind\":\"local_annotation\",\"annotation\":\"@NonNull\",\"annotation_range\":{\"start_offset\":"
                                + annotationStart
                                + ",\"end_offset\":"
                                + (annotationStart + "@NonNull".length())
                                + "}}}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A1\",\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        return trace;
    }

    private static Path sourceWithReceiverDereference() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-receiver-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  void f(Example e) {",
                        "    e.toString();",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        return source;
    }

    private static Path traceForReceiverDereference(Path source) throws Exception {
        Path trace = Files.createTempFile("checker-reconcile-receiver-trace", ".jsonl");
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int expressionStart = text.indexOf("e.toString()");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A1\",\"kind\":\"receiver_qualifier\",\"slot\":\"receiver:e\",\"type\":\"@Nullable Example\",\"editable\":false,\"weight\":1000,\"source_target\":{\"kind\":\"receiver_expression\",\"expression\":\"e\",\"expression_range\":{\"start_offset\":"
                                + expressionStart
                                + ",\"end_offset\":"
                                + (expressionStart + "e".length())
                                + "}}}",
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"receiver_contract\",\"slot\":\"method-contract:nonnull-receiver\",\"type\":\"@NonNull Example\",\"editable\":false,\"weight\":1000}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"dereference\",\"relation\":\"receiver_nonnull\",\"got\":\"@Nullable Example\",\"want\":\"@NonNull Example\",\"dependencies\":[\"A1\",\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"dereference.of.nullable\",\"message\":\"dereference.of.nullable\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        return trace;
    }

    private static Path sourceWithMonotonicArrayComponent() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-monotonic-array-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.MonotonicNonNull;",
                        "class Example {",
                        "  void f() {",
                        "    @MonotonicNonNull Object[] o1 = new @MonotonicNonNull Object[10];",
                        "    o1[0] = null;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        return source;
    }

    private static Path traceForMonotonicArrayComponent(Path source) throws Exception {
        Path trace = Files.createTempFile("checker-reconcile-monotonic-array-trace", ".jsonl");
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int declarationStart = text.indexOf("@MonotonicNonNull Object[] o1");
        int declarationEnd = text.indexOf(";", declarationStart) + 1;
        int nullStart = text.indexOf("null");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A1\",\"kind\":\"actual_qualifier\",\"slot\":\"expr:null\",\"type\":\"@Nullable NullType\",\"editable\":true,\"weight\":5,\"range\":{\"start_offset\":"
                                + nullStart
                                + ",\"end_offset\":"
                                + (nullStart + "null".length())
                                + "}}",
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"target:o1[0]\",\"type\":\"@MonotonicNonNull Object\",\"editable\":true,\"weight\":5,\"range\":{\"start_offset\":"
                                + declarationStart
                                + ",\"end_offset\":"
                                + declarationEnd
                                + ",\"start_line\":4}}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable NullType\",\"want\":\"@MonotonicNonNull Object\",\"dependencies\":[\"A1\",\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}"),
                StandardCharsets.UTF_8);
        return trace;
    }

    private static Path sourceWithTwoExplicitLocals() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-two-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.NonNull;",
                        "class Example {",
                        "  void f(String s) {",
                        "    @NonNull String t = s;",
                        "    @NonNull String u = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        return source;
    }

    private static Path traceForTwoExplicitLocals(Path source) throws Exception {
        Path trace = Files.createTempFile("checker-reconcile-two-trace", ".jsonl");
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int firstAnnotationStart = text.indexOf("@NonNull");
        int secondAnnotationStart = text.indexOf("@NonNull", firstAnnotationStart + 1);
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A1\",\"kind\":\"actual_qualifier\",\"slot\":\"expr:s\",\"type\":\"@Nullable String\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"local:t\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,\"source_target\":{\"kind\":\"local_annotation\",\"annotation\":\"@NonNull\",\"annotation_range\":{\"start_offset\":"
                                + firstAnnotationStart
                                + ",\"end_offset\":"
                                + (firstAnnotationStart + "@NonNull".length())
                                + "}}}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A1\",\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}",
                        "{\"event\":\"assumption\",\"id\":\"A3\",\"kind\":\"actual_qualifier\",\"slot\":\"expr:s\",\"type\":\"@Nullable String\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"assumption\",\"id\":\"A4\",\"kind\":\"target_qualifier\",\"slot\":\"local:u\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,\"source_target\":{\"kind\":\"local_annotation\",\"annotation\":\"@NonNull\",\"annotation_range\":{\"start_offset\":"
                                + secondAnnotationStart
                                + ",\"end_offset\":"
                                + (secondAnnotationStart + "@NonNull".length())
                                + "}}}",
                        "{\"event\":\"obligation\",\"id\":\"O2\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A3\",\"A4\"],\"result\":\"error\",\"diagnostic_id\":\"E2\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E2\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O2\"}"),
                StandardCharsets.UTF_8);
        return trace;
    }

    private static Path sourceWithThreeExplicitLocals() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-three-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.NonNull;",
                        "class Example {",
                        "  void f(String s) {",
                        "    @NonNull String t = s;",
                        "    @NonNull String u = s;",
                        "    @NonNull String v = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        return source;
    }

    private static Path traceForThreeExplicitLocals(Path source) throws Exception {
        Path trace = Files.createTempFile("checker-reconcile-three-trace", ".jsonl");
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int firstAnnotationStart = text.indexOf("@NonNull");
        int secondAnnotationStart = text.indexOf("@NonNull", firstAnnotationStart + 1);
        int thirdAnnotationStart = text.indexOf("@NonNull", secondAnnotationStart + 1);
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A1\",\"kind\":\"actual_qualifier\",\"slot\":\"expr:s\",\"type\":\"@Nullable String\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"local:t\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,\"source_target\":{\"kind\":\"local_annotation\",\"annotation\":\"@NonNull\",\"annotation_range\":{\"start_offset\":"
                                + firstAnnotationStart
                                + ",\"end_offset\":"
                                + (firstAnnotationStart + "@NonNull".length())
                                + "}}}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A1\",\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}",
                        "{\"event\":\"assumption\",\"id\":\"A3\",\"kind\":\"actual_qualifier\",\"slot\":\"expr:s\",\"type\":\"@Nullable String\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"assumption\",\"id\":\"A4\",\"kind\":\"target_qualifier\",\"slot\":\"local:u\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,\"source_target\":{\"kind\":\"local_annotation\",\"annotation\":\"@NonNull\",\"annotation_range\":{\"start_offset\":"
                                + secondAnnotationStart
                                + ",\"end_offset\":"
                                + (secondAnnotationStart + "@NonNull".length())
                                + "}}}",
                        "{\"event\":\"obligation\",\"id\":\"O2\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A3\",\"A4\"],\"result\":\"error\",\"diagnostic_id\":\"E2\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E2\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O2\"}",
                        "{\"event\":\"assumption\",\"id\":\"A5\",\"kind\":\"actual_qualifier\",\"slot\":\"expr:s\",\"type\":\"@Nullable String\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"assumption\",\"id\":\"A6\",\"kind\":\"target_qualifier\",\"slot\":\"local:v\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,\"source_target\":{\"kind\":\"local_annotation\",\"annotation\":\"@NonNull\",\"annotation_range\":{\"start_offset\":"
                                + thirdAnnotationStart
                                + ",\"end_offset\":"
                                + (thirdAnnotationStart + "@NonNull".length())
                                + "}}}",
                        "{\"event\":\"obligation\",\"id\":\"O3\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A5\",\"A6\"],\"result\":\"error\",\"diagnostic_id\":\"E3\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E3\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O3\"}"),
                StandardCharsets.UTF_8);
        return trace;
    }

    private static Path traceWithSharedFieldSlot() throws Exception {
        Path trace = Files.createTempFile("checker-reconcile-field-group", ".jsonl");
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A1\",\"kind\":\"actual_qualifier\",\"slot\":\"expr:s1\",\"type\":\"@Nullable String\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"field:f\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"field_assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A1\",\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}",
                        "{\"event\":\"assumption\",\"id\":\"A3\",\"kind\":\"actual_qualifier\",\"slot\":\"expr:s2\",\"type\":\"@Nullable String\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"assumption\",\"id\":\"A4\",\"kind\":\"target_qualifier\",\"slot\":\"field:f\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5}",
                        "{\"event\":\"obligation\",\"id\":\"O2\",\"kind\":\"field_assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A3\",\"A4\"],\"result\":\"error\",\"diagnostic_id\":\"E2\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E2\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O2\"}"),
                StandardCharsets.UTF_8);
        return trace;
    }

    private static Path sourceWithSharedField() throws Exception {
        Path source = Files.createTempFile("checker-reconcile-shared-field-source", ".java");
        Files.write(
                source,
                Arrays.asList(
                        "class Example {",
                        "  @NonNull String f;",
                        "  void first(String s) {",
                        "    this.f = s;",
                        "  }",
                        "  void second(String s) {",
                        "    this.f = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);
        return source;
    }

    private static Path traceForSharedFieldSourceTarget(Path source) throws Exception {
        Path trace = Files.createTempFile("checker-reconcile-shared-field-trace", ".jsonl");
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int annotationStart = text.indexOf("@NonNull");
        String sourceTarget =
                "\"source_target\":{\"kind\":\"field_annotation\",\"annotation\":\"@NonNull\","
                        + "\"annotation_range\":{\"start_offset\":"
                        + annotationStart
                        + ",\"end_offset\":"
                        + (annotationStart + "@NonNull".length())
                        + "}}";
        Files.write(
                trace,
                Arrays.asList(
                        "{\"event\":\"assumption\",\"id\":\"A1\",\"kind\":\"target_qualifier\",\"slot\":\"field:f\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,"
                                + sourceTarget
                                + "}",
                        "{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"field_assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A1\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}",
                        "{\"event\":\"assumption\",\"id\":\"A2\",\"kind\":\"target_qualifier\",\"slot\":\"field:f\",\"type\":\"@NonNull String\",\"editable\":true,\"weight\":5,"
                                + sourceTarget
                                + "}",
                        "{\"event\":\"obligation\",\"id\":\"O2\",\"kind\":\"field_assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E2\"}",
                        "{\"event\":\"diagnostic\",\"id\":\"E2\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O2\"}"),
                StandardCharsets.UTF_8);
        return trace;
    }

    private static Path fakeJavacAcceptingNullableLocal() throws Exception {
        Path javac = Files.createTempFile("checker-reconcile-fake-javac", ".sh");
        Files.write(
                javac,
                Arrays.asList(
                        "#!/bin/sh",
                        "for last do :; done",
                        "if grep -q '@Nullable String t = s;' \"$last\"; then",
                        "  exit 0",
                        "fi",
                        "echo \"$last:1: error: fake nullness error\"",
                        "exit 1"),
                StandardCharsets.UTF_8);
        javac.toFile().setExecutable(true);
        return javac;
    }

    private static Path fakeJavacAcceptingNullableField() throws Exception {
        Path javac = Files.createTempFile("checker-reconcile-fake-javac-field", ".sh");
        Files.write(
                javac,
                Arrays.asList(
                        "#!/bin/sh",
                        "for last do :; done",
                        "if grep -q '@Nullable String f;' \"$last\"; then",
                        "  exit 0",
                        "fi",
                        "echo \"$last:1: error: fake nullness error\"",
                        "echo \"$last:2: error: fake nullness error\"",
                        "exit 2"),
                StandardCharsets.UTF_8);
        javac.toFile().setExecutable(true);
        return javac;
    }

    private static Path fakeJavacAcceptingNullableArrayComponent() throws Exception {
        Path javac = Files.createTempFile("checker-reconcile-fake-javac-array-component", ".sh");
        Files.write(
                javac,
                Arrays.asList(
                        "#!/bin/sh",
                        "for last do :; done",
                        "if grep -q '@Nullable Object\\[\\] o1' \"$last\"; then",
                        "  exit 0",
                        "fi",
                        "echo \"$last:1: error: fake nullness error\"",
                        "exit 1"),
                StandardCharsets.UTF_8);
        javac.toFile().setExecutable(true);
        return javac;
    }

    private static Path fakeJavacAcceptingNullableSecondLocal() throws Exception {
        Path javac = Files.createTempFile("checker-reconcile-fake-javac-second", ".sh");
        Files.write(
                javac,
                Arrays.asList(
                        "#!/bin/sh",
                        "for last do :; done",
                        "if grep -q '@Nullable String u = s;' \"$last\"; then",
                        "  exit 0",
                        "fi",
                        "echo \"$last:1: error: fake nullness error\"",
                        "exit 1"),
                StandardCharsets.UTF_8);
        javac.toFile().setExecutable(true);
        return javac;
    }

    private static Path fakeJavacWritingTrace() throws Exception {
        Path javac = Files.createTempFile("checker-reconcile-fake-javac-trace", ".sh");
        Files.write(
                javac,
                Arrays.asList(
                        "#!/bin/sh",
                        "trace=''",
                        "for arg do",
                        "  case \"$arg\" in",
                        "    -AexportNullnessTrace=*) trace=${arg#-AexportNullnessTrace=} ;;",
                        "  esac",
                        "  last=$arg",
                        "done",
                        "if [ -n \"$trace\" ]; then",
                        "  printf '%s\\n' '{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"fake\",\"obligation\":\"O1\"}' > \"$trace\"",
                        "fi",
                        "echo \"$last:1: error: fake nullness error\"",
                        "exit 1"),
                StandardCharsets.UTF_8);
        javac.toFile().setExecutable(true);
        return javac;
    }

    private static Path fakeJavacExportingFollowupTrace() throws Exception {
        Path javac = Files.createTempFile("checker-reconcile-fake-javac-followup", ".sh");
        Files.write(
                javac,
                Arrays.asList(
                        "#!/bin/sh",
                        "trace=''",
                        "for arg do",
                        "  case \"$arg\" in",
                        "    -AexportNullnessTrace=*) trace=${arg#-AexportNullnessTrace=} ;;",
                        "  esac",
                        "  last=$arg",
                        "done",
                        "if ! grep -q '@Nullable String t = s;' \"$last\"; then",
                        "  echo \"$last:1: error: fake nullness error\"",
                        "  exit 1",
                        "fi",
                        "if ! grep -q '@Nullable String u = s;' \"$last\"; then",
                        "  if [ -n \"$trace\" ]; then",
                        "    offset=$(grep -b -o '@NonNull String u = s;' \"$last\" | head -n 1 | cut -d: -f1)",
                        "    end=$((offset + 8))",
                        "    printf '%s\\n' \\",
                        "      '{\"event\":\"assumption\",\"id\":\"A1\",\"kind\":\"actual_qualifier\",\"slot\":\"expr:s\",\"type\":\"@Nullable String\",\"editable\":true,\"weight\":5}' \\",
                        "      \"{\\\"event\\\":\\\"assumption\\\",\\\"id\\\":\\\"A2\\\",\\\"kind\\\":\\\"target_qualifier\\\",\\\"slot\\\":\\\"local:u\\\",\\\"type\\\":\\\"@NonNull String\\\",\\\"editable\\\":true,\\\"weight\\\":5,\\\"source_target\\\":{\\\"kind\\\":\\\"local_annotation\\\",\\\"annotation\\\":\\\"@NonNull\\\",\\\"annotation_range\\\":{\\\"start_offset\\\":$offset,\\\"end_offset\\\":$end}}}\" \\",
                        "      '{\"event\":\"obligation\",\"id\":\"O1\",\"kind\":\"assignment\",\"relation\":\"subtype\",\"got\":\"@Nullable String\",\"want\":\"@NonNull String\",\"dependencies\":[\"A1\",\"A2\"],\"result\":\"error\",\"diagnostic_id\":\"E1\"}' \\",
                        "      '{\"event\":\"diagnostic\",\"id\":\"E1\",\"error_kind\":\"assignment.type.incompatible\",\"message\":\"assignment.type.incompatible\",\"obligation\":\"O1\"}' > \"$trace\"",
                        "  fi",
                        "  echo \"$last:2: error: fake nullness error\"",
                        "  exit 1",
                        "fi",
                        "exit 0"),
                StandardCharsets.UTF_8);
        javac.toFile().setExecutable(true);
        return javac;
    }

    private static Path fakeJavacCountingLocalErrors() throws Exception {
        Path javac = Files.createTempFile("checker-reconcile-fake-javac-count", ".sh");
        Files.write(
                javac,
                Arrays.asList(
                        "#!/bin/sh",
                        "for last do :; done",
                        "count=0",
                        "if ! grep -q '@Nullable String t = s;' \"$last\"; then",
                        "  echo \"$last:1: error: fake nullness error\"",
                        "  count=$((count + 1))",
                        "fi",
                        "if ! grep -q '@Nullable String u = s;' \"$last\"; then",
                        "  echo \"$last:2: error: fake nullness error\"",
                        "  count=$((count + 1))",
                        "fi",
                        "exit $count"),
                StandardCharsets.UTF_8);
        javac.toFile().setExecutable(true);
        return javac;
    }

    private static Path fakeJavacCountingThreeLocalErrors() throws Exception {
        Path javac = Files.createTempFile("checker-reconcile-fake-javac-count-three", ".sh");
        Files.write(
                javac,
                Arrays.asList(
                        "#!/bin/sh",
                        "for last do :; done",
                        "count=0",
                        "if ! grep -q '@Nullable String t = s;' \"$last\"; then",
                        "  echo \"$last:1: error: fake nullness error\"",
                        "  count=$((count + 1))",
                        "fi",
                        "if ! grep -q '@Nullable String u = s;' \"$last\"; then",
                        "  echo \"$last:2: error: fake nullness error\"",
                        "  count=$((count + 1))",
                        "fi",
                        "if ! grep -q '@Nullable String v = s;' \"$last\"; then",
                        "  echo \"$last:3: error: fake nullness error\"",
                        "  count=$((count + 1))",
                        "fi",
                        "exit $count"),
                StandardCharsets.UTF_8);
        javac.toFile().setExecutable(true);
        return javac;
    }

    @Test
    public void minesCfTestDiagnostics() throws Exception {
        Path root = Files.createTempDirectory("checker-reconcile-cf-tests");
        Path file = root.resolve("Example.java");
        Files.write(
                file,
                Arrays.asList(
                        "class Example {",
                        "  void f(String s) {",
                        "    // :: error: (assignment.type.incompatible)",
                        "    @NonNull String t = s;",
                        "    // :: error: (argument.type.incompatible) :: error: (return.type.incompatible)",
                        "    takes(s);",
                        "    // :: error: (assignment.type.incompatible)",
                        "    this.field = s;",
                        "    // :: error: (assignment.type.incompatible)",
                        "    Object x = (@NonNull Object) s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);

        CfTestDiagnosticMiner miner = new CfTestDiagnosticMiner();
        List<Candidate> candidates = miner.mine(root);

        assertEquals(5, candidates.size());
        assertEquals(
                3, miner.countsByKind(candidates).get("assignment.type.incompatible").intValue());
        assertEquals(
                Integer.valueOf(1),
                miner.countsByKind(candidates).get("argument.type.incompatible"));
        assertEquals(
                Integer.valueOf(1), miner.countsByKind(candidates).get("return.type.incompatible"));
        assertTrue(
                miner.firstPerKind(candidates).stream()
                        .anyMatch(
                                candidate ->
                                        candidate
                                                .toString()
                                                .equals(
                                                        "Example.java:3 assignment.type.incompatible")));
        List<Candidate> repairs = miner.likelyLocalAnnotationRepairs(candidates);
        assertEquals(1, repairs.size());
        assertEquals("@NonNull String t = s;", repairs.get(0).codeLine());
        List<Candidate> repairCandidates = miner.likelyRepairCandidates(candidates);
        assertEquals(3, repairCandidates.size());
        assertEquals(RepairKind.CHANGE_QUALIFIER, repairCandidates.get(0).likelyRepairKind());
        assertEquals(
                Arrays.asList(RepairKind.CHANGE_QUALIFIER, RepairKind.ADD_NULL_CHECK),
                repairCandidates.get(1).likelyRepairKinds());
        assertEquals(
                Arrays.asList(RepairKind.CHANGE_QUALIFIER, RepairKind.ADD_NULL_CHECK),
                repairCandidates.get(2).likelyRepairKinds());
    }
}
