package org.checkerframework.checker.test.junit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.checkerframework.checker.nullness.NullnessChecker;
import org.checkerframework.checker.test.junit.repair.RepairCase;
import org.checkerframework.checker.test.junit.repair.RepairHarness;
import org.checkerframework.framework.test.TestConfiguration;
import org.checkerframework.framework.test.TestConfigurationBuilder;
import org.checkerframework.framework.test.TestUtilities;
import org.checkerframework.framework.test.TypecheckExecutor;
import org.checkerframework.framework.test.TypecheckResult;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** JUnit tests for Nullness trace export. */
public class NullnessTraceExportTest {

    @Test
    public void traceOptionWritesLinkedAssignmentEvents() throws Exception {
        Path testDir = Files.createTempDirectory("nullness-trace-test");
        Path source = testDir.resolve("TraceAssignment.java");
        Path trace = testDir.resolve("trace.jsonl");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.NonNull;",
                        "import org.checkerframework.checker.nullness.qual.Nullable;",
                        "class TraceAssignment {",
                        "  void f(@Nullable String s) {",
                        "    // :: error: (assignment.type.incompatible)",
                        "    @NonNull String t = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);

        TypecheckResult result = runNullness(testDir, source, "-AexportNullnessTrace=" + trace);

        TestUtilities.assertTestDidNotFail(result);
        String jsonl = new String(Files.readAllBytes(trace), StandardCharsets.UTF_8);
        assertTrue(jsonl.contains("\"event\":\"assumption\""));
        assertTrue(jsonl.contains("\"event\":\"obligation\""));
        assertTrue(jsonl.contains("\"event\":\"diagnostic\""));
        assertTrue(jsonl.contains("\"kind\":\"assignment\""));
        assertTrue(jsonl.contains("\"diagnostic_id\":\"E1\""));
        assertTrue(jsonl.contains("\"obligation\":\"O1\""));
        assertTrue(jsonl.contains("\"dependencies\":[\"A1\",\"A2\"]"));
        assertTrue(jsonl.contains("\"actual_range\""));
        assertTrue(jsonl.contains("\"expected_range\""));
        assertTrue(jsonl.contains("\"start_offset\""));
        assertTrue(jsonl.contains("\"end_offset\""));
        assertTrue(jsonl.contains("\"source_target\""));
        assertTrue(jsonl.contains("\"kind\":\"local_annotation\""));
        assertTrue(jsonl.contains("\"annotation\":\"@NonNull\""));
        assertTrue(jsonl.contains("\"annotation_range\""));
        assertTrue(jsonl.contains("\"declaration_range\""));
        assertTrue(jsonl.contains("incompatible types in assignment."));
        assertTrue(jsonl.contains("found   : @Nullable String"));
        assertTrue(jsonl.contains("required: @NonNull String"));
    }

    @Test
    public void noTraceOptionLeavesNoTraceFile() throws Exception {
        Path testDir = Files.createTempDirectory("nullness-no-trace-test");
        Path source = testDir.resolve("NoTrace.java");
        Path trace = testDir.resolve("trace.jsonl");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.NonNull;",
                        "import org.checkerframework.checker.nullness.qual.Nullable;",
                        "class NoTrace {",
                        "  void f(@Nullable String s) {",
                        "    // :: error: (assignment.type.incompatible)",
                        "    @NonNull String t = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);

        TypecheckResult result = runNullness(testDir, source);

        TestUtilities.assertTestDidNotFail(result);
        assertFalse(Files.exists(trace));
    }

    @Test
    public void invalidTracePathReportsClearError() throws Exception {
        Path testDir = Files.createTempDirectory("nullness-invalid-trace-test");
        Path source = testDir.resolve("InvalidTrace.java");
        Files.write(
                source,
                Arrays.asList(
                        "class InvalidTrace {",
                        "  void f(String s) {",
                        "    String t = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);

        TypecheckResult result = runNullness(testDir, source, "-AexportNullnessTrace=" + testDir);

        assertFalse(result.getCompilationResult().compiledWithoutError());
        assertTrue(
                result.getCompilationResult().getDiagnostics().stream()
                        .anyMatch(
                                diagnostic ->
                                        diagnostic
                                                .getMessage(null)
                                                .contains("Could not open Nullness trace file")));
    }

    @Test
    public void traceOptionWritesReturnAndArgumentEvents() throws Exception {
        Path testDir = Files.createTempDirectory("nullness-trace-return-argument-test");
        Path source = testDir.resolve("TraceReturnArgument.java");
        Path trace = testDir.resolve("trace.jsonl");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.NonNull;",
                        "import org.checkerframework.checker.nullness.qual.Nullable;",
                        "class TraceReturnArgument {",
                        "  void takes(@NonNull String p) {}",
                        "  @NonNull String returns(@Nullable String s) {",
                        "    // :: error: (return.type.incompatible)",
                        "    return s;",
                        "  }",
                        "  void passes(@Nullable String s) {",
                        "    // :: error: (argument.type.incompatible)",
                        "    takes(s);",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);

        TypecheckResult result = runNullness(testDir, source, "-AexportNullnessTrace=" + trace);

        TestUtilities.assertTestDidNotFail(result);
        String jsonl = new String(Files.readAllBytes(trace), StandardCharsets.UTF_8);
        assertTrue(jsonl.contains("\"kind\":\"return\""));
        assertTrue(jsonl.contains("\"error_kind\":\"return.type.incompatible\""));
        assertTrue(jsonl.contains("\"source_target\""));
        assertTrue(jsonl.contains("\"kind\":\"return_annotation\""));
        assertTrue(jsonl.contains("\"kind\":\"method_argument\""));
        assertTrue(jsonl.contains("\"error_kind\":\"argument.type.incompatible\""));
        assertTrue(jsonl.contains("\"kind\":\"argument_expression\""));
        assertTrue(jsonl.contains("\"expression\":\"s\""));
        assertTrue(jsonl.contains("\"expression_range\""));
        assertTrue(jsonl.contains("\"argument_index\":0"));
        assertTrue(jsonl.contains("\"formal_parameter\":\"p\""));
        assertTrue(jsonl.contains("\"invocation_kind\":\"method\""));
        assertTrue(jsonl.contains("\"standalone_invocation\":true"));
        assertTrue(jsonl.contains("\"invocation_range\""));
        assertTrue(jsonl.contains("\"statement_range\""));
        assertTrue(jsonl.contains("\"kind\":\"parameter_annotation\""));
        assertTrue(jsonl.contains("\"annotation\":\"@NonNull\""));
        assertTrue(jsonl.contains("\"annotation_range\""));
    }

    @Test
    public void traceOptionWritesFieldSourceTarget() throws Exception {
        Path testDir = Files.createTempDirectory("nullness-trace-field-test");
        Path source = testDir.resolve("TraceFieldAssignment.java");
        Path trace = testDir.resolve("trace.jsonl");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.NonNull;",
                        "import org.checkerframework.checker.nullness.qual.Nullable;",
                        "class TraceFieldAssignment {",
                        "  @NonNull String f = \"\";",
                        "  void assign(@Nullable String s) {",
                        "    // :: error: (assignment.type.incompatible)",
                        "    this.f = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);

        TypecheckResult result = runNullness(testDir, source, "-AexportNullnessTrace=" + trace);

        TestUtilities.assertTestDidNotFail(result);
        String jsonl = new String(Files.readAllBytes(trace), StandardCharsets.UTF_8);
        assertTrue(jsonl.contains("\"kind\":\"field_assignment\""));
        assertTrue(jsonl.contains("\"source_target\""));
        assertTrue(jsonl.contains("\"kind\":\"field_annotation\""));
        assertTrue(jsonl.contains("\"annotation\":\"@NonNull\""));
        assertTrue(jsonl.contains("\"annotation_range\""));
    }

    @Test
    public void traceOptionWritesNullableReceiverDereferenceEvents() throws Exception {
        Path testDir = Files.createTempDirectory("nullness-trace-receiver-test");
        Path source = testDir.resolve("TraceReceiverDereference.java");
        Path trace = testDir.resolve("trace.jsonl");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.Nullable;",
                        "class TraceReceiverDereference {",
                        "  void f(@Nullable String s) {",
                        "    // :: error: (dereference.of.nullable)",
                        "    s.length();",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);

        TypecheckResult result = runNullness(testDir, source, "-AexportNullnessTrace=" + trace);

        TestUtilities.assertTestDidNotFail(result);
        String jsonl = new String(Files.readAllBytes(trace), StandardCharsets.UTF_8);
        assertTrue(jsonl.contains("\"kind\":\"dereference\""));
        assertTrue(jsonl.contains("\"relation\":\"receiver_nonnull\""));
        assertTrue(jsonl.contains("\"kind\":\"receiver_qualifier\""));
        assertTrue(jsonl.contains("\"kind\":\"receiver_contract\""));
        assertTrue(jsonl.contains("\"error_kind\":\"dereference.of.nullable\""));
        assertTrue(jsonl.contains("\"slot\":\"receiver:s\""));
        assertTrue(jsonl.contains("\"slot\":\"method-contract:nonnull-receiver\""));
        assertTrue(jsonl.contains("\"kind\":\"receiver_expression\""));
        assertTrue(jsonl.contains("\"expression\":\"s\""));
        assertTrue(jsonl.contains("\"expression_range\""));
    }

    @Test
    public void traceOptionWritesNullableContextEvents() throws Exception {
        Path testDir = Files.createTempDirectory("nullness-trace-context-test");
        Path source = testDir.resolve("TraceNullableContexts.java");
        Path trace = testDir.resolve("trace.jsonl");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.Nullable;",
                        "class TraceNullableContexts {",
                        "  void f(@Nullable Boolean b, @Nullable Integer i,",
                        "      String @Nullable [] values, @Nullable Iterable<String> iterable) {",
                        "    // :: error: (condition.nullable)",
                        "    if (b) {}",
                        "    // :: error: (unboxing.of.nullable)",
                        "    int unboxed = i;",
                        "    // :: error: (accessing.nullable)",
                        "    String first = values[0];",
                        "    // :: error: (iterating.over.nullable)",
                        "    for (String value : iterable) {}",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);

        TypecheckResult result = runNullness(testDir, source, "-AexportNullnessTrace=" + trace);

        TestUtilities.assertTestDidNotFail(result);
        String jsonl = new String(Files.readAllBytes(trace), StandardCharsets.UTF_8);
        assertTrue(jsonl.contains("\"kind\":\"condition\""));
        assertTrue(jsonl.contains("\"kind\":\"unboxing\""));
        assertTrue(jsonl.contains("\"kind\":\"array_access\""));
        assertTrue(jsonl.contains("\"kind\":\"iteration\""));
        assertTrue(jsonl.contains("\"relation\":\"nonnull\""));
        assertTrue(jsonl.contains("\"kind\":\"nonnull_contract\""));
        assertTrue(jsonl.contains("\"error_kind\":\"condition.nullable\""));
        assertTrue(jsonl.contains("\"error_kind\":\"unboxing.of.nullable\""));
        assertTrue(jsonl.contains("\"error_kind\":\"accessing.nullable\""));
        assertTrue(jsonl.contains("\"error_kind\":\"iterating.over.nullable\""));
        assertTrue(jsonl.contains("\"kind\":\"condition_expression\""));
        assertTrue(jsonl.contains("\"kind\":\"unboxing_expression\""));
        assertTrue(jsonl.contains("\"kind\":\"array_expression\""));
        assertTrue(jsonl.contains("\"kind\":\"iteration_expression\""));
        assertTrue(jsonl.contains("\"expression_range\""));
    }

    @Test
    public void checkerReconcileRepairsAndValidatesLocalAnnotation() throws Exception {
        Path testDir = Files.createTempDirectory("nullness-reconcile-e2e-test");
        Path source = testDir.resolve("TraceRepair.java");
        Path trace = testDir.resolve("trace.jsonl");
        Path patched = testDir.resolve("TraceRepairPatched.java");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.NonNull;",
                        "import org.checkerframework.checker.nullness.qual.Nullable;",
                        "class TraceRepair {",
                        "  void f(@Nullable String s) {",
                        "    @NonNull String t = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);

        RepairCase repairCase =
                new RepairCase(
                        "local annotation repair",
                        source,
                        trace,
                        patched,
                        "assignment.type.incompatible",
                        Arrays.asList("@Nullable String t = s;"),
                        RepairCase.ValidationMode.MUST_PASS);
        RepairHarness.RepairResult result = new RepairHarness().run(repairCase);

        assertTrue(result.beforeDiagnosticCount() > result.afterDiagnosticCount());
        assertTrue(result.outcome() == RepairHarness.Outcome.PATCHED_AND_PASSED);
    }

    @Test
    public void repairHarnessRepairsExplicitReturnAnnotation() throws Exception {
        Path testDir = Files.createTempDirectory("nullness-reconcile-return-test");
        Path source = testDir.resolve("TraceReturnRepair.java");
        Path trace = testDir.resolve("trace.jsonl");
        Path patched = testDir.resolve("TraceReturnRepairPatched.java");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.NonNull;",
                        "import org.checkerframework.checker.nullness.qual.Nullable;",
                        "class TraceReturnRepair {",
                        "  @NonNull String f(@Nullable String s) {",
                        "    return s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);

        RepairCase repairCase =
                new RepairCase(
                        "return annotation repair",
                        source,
                        trace,
                        patched,
                        "return.type.incompatible",
                        Arrays.asList("@Nullable String f(@Nullable String s)"),
                        RepairCase.ValidationMode.MUST_PASS);
        RepairHarness.RepairResult result = new RepairHarness().run(repairCase);

        assertTrue(result.beforeDiagnosticCount() > result.afterDiagnosticCount());
        assertTrue(result.outcome() == RepairHarness.Outcome.PATCHED_AND_PASSED);
    }

    @Test
    public void repairHarnessAcceptsDiagnosticDecrease() throws Exception {
        Path testDir = Files.createTempDirectory("nullness-reconcile-decrease-test");
        Path source = testDir.resolve("TraceRepairDecrease.java");
        Path trace = testDir.resolve("trace.jsonl");
        Path patched = testDir.resolve("TraceRepairDecreasePatched.java");
        Files.write(
                source,
                Arrays.asList(
                        "import org.checkerframework.checker.nullness.qual.NonNull;",
                        "import org.checkerframework.checker.nullness.qual.Nullable;",
                        "class TraceRepairDecrease {",
                        "  void f(@Nullable String s) {",
                        "    @NonNull String t = s;",
                        "    @NonNull String u = s;",
                        "  }",
                        "}"),
                StandardCharsets.UTF_8);

        RepairCase repairCase =
                new RepairCase(
                        "local annotation repair decreases diagnostics",
                        source,
                        trace,
                        patched,
                        "assignment.type.incompatible",
                        Arrays.asList("@Nullable String t = s;"),
                        RepairCase.ValidationMode.MUST_DECREASE);
        RepairHarness.RepairResult result = new RepairHarness().run(repairCase);

        assertTrue(result.beforeDiagnosticCount() > result.afterDiagnosticCount());
        assertTrue(result.outcome() == RepairHarness.Outcome.PATCHED_BUT_FAILED);
    }

    private TypecheckResult runNullness(Path testDir, Path source, String... extraOptions) {
        TypecheckResult result = compileNullness(testDir, source, extraOptions);
        return result;
    }

    private TypecheckResult compileNullness(Path testDir, Path source, String... extraOptions) {
        List<String> options =
                extraOptions.length == 0 ? Collections.emptyList() : Arrays.asList(extraOptions);
        TestConfiguration config =
                TestConfigurationBuilder.buildDefaultConfiguration(
                        testDir.toString(),
                        new File(source.toString()),
                        NullnessChecker.class,
                        options,
                        false);
        return new TypecheckExecutor().runTest(config);
    }
}
