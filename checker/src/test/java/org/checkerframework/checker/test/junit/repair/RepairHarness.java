package org.checkerframework.checker.test.junit.repair;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.checkerframework.checker.nullness.NullnessChecker;
import org.checkerframework.framework.test.TestConfiguration;
import org.checkerframework.framework.test.TestConfigurationBuilder;
import org.checkerframework.framework.test.TypecheckExecutor;
import org.checkerframework.framework.test.TypecheckResult;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import checker_reconcile.constraints.TraceModel;
import checker_reconcile.diagnosis.RepairCandidateSet;
import checker_reconcile.diagnosis.SearchReportJson;
import checker_reconcile.diagnosis.SearchReportParser;
import checker_reconcile.diagnosis.ValidationBackedRepairSearch;
import checker_reconcile.repair.AgentContextJson;
import checker_reconcile.repair.AgentContextParser;
import checker_reconcile.repair.AgentProposalParser;
import checker_reconcile.repair.Patcher;
import checker_reconcile.repair.PlannedRepair;
import checker_reconcile.repair.RepairPlanner;
import checker_reconcile.repair.SourceEdit;
import checker_reconcile.repair.SuggestedRepair;
import checker_reconcile.repair.Validation;
import checker_reconcile.repair.ValidationReportJson;
import checker_reconcile.repair.ValidationReportParser;
import checker_reconcile.trace.TraceEvent;
import checker_reconcile.trace.TraceParser;

/** CF-facing repair harness for strict regression cases. */
public final class RepairHarness {
    public RepairResult run(RepairCase repairCase) throws Exception {
        TypecheckResult before =
                compileNullness(
                        repairCase.source().getParent(),
                        repairCase.source(),
                        "-AexportNullnessTrace=" + repairCase.trace());
        assertFalse(repairCase.name(), before.getCompilationResult().compiledWithoutError());

        List<TraceEvent> events = new TraceParser().parse(repairCase.trace());
        TraceModel model = TraceModel.fromEvents(events);
        assertFalse(repairCase.name() + " trace diagnostics", model.diagnostics.isEmpty());

        TraceModel.DiagnosticSlice slice =
                firstSliceWithKind(model, repairCase.expectedDiagnosticKind());
        List<SuggestedRepair> repairs = new RepairPlanner().plan(repairCase.source(), slice);
        assertExpectedRepairs(repairCase, repairs);
        if (repairCase.runner() == RepairCase.Runner.SEARCH) {
            List<String> searchReportLines = new ArrayList<>();
            ValidationBackedRepairSearch.Listener searchReportListener =
                    searchReportListener(searchReportLines);
            ValidationBackedRepairSearch.Result searchResult =
                    new ValidationBackedRepairSearch()
                            .search(
                                    repairCase.source(),
                                    model,
                                    repairCase.patched(),
                                    javac(),
                                    checkerAllJar(),
                                    repairCase.searchValidationMode(),
                                    repairCase.allowedRisk(),
                                    searchReportListener,
                                    repairCase.maxCandidateSize(),
                                    repairCase.maxSearchCandidates(),
                                    repairCase.includeSketchEdits());
            if (repairCase.expectSearchReport()) {
                SearchReportJson json = new SearchReportJson();
                searchReportLines.add(json.summary(searchResult));
                Path report =
                        repairCase.patched().resolveSibling(repairCase.name() + ".search.jsonl");
                Files.write(report, searchReportLines, StandardCharsets.UTF_8);
                assertExpectedSearchReport(repairCase, report);
            }
            if (repairCase.expectedSearchAccepted()) {
                assertTrue(repairCase.name() + " search accepted a patch", searchResult.accepted());
                if (repairCase.expectedSearchCandidateSize() != null) {
                    assertTrue(
                            repairCase.name()
                                    + " expected accepted candidate size "
                                    + repairCase.expectedSearchCandidateSize(),
                            searchResult.candidateSet().candidates().size()
                                    == repairCase.expectedSearchCandidateSize());
                }
            } else {
                assertFalse(
                        repairCase.name() + " search should reject all candidates",
                        searchResult.accepted());
                Files.copy(
                        repairCase.source(),
                        repairCase.patched(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } else if (repairCase.runner() == RepairCase.Runner.AGENT) {
            runAgentRepair(repairCase, slice, repairs);
        } else {
            new Patcher().writePatched(repairCase.source(), repairCase.patched(), slice);
        }

        String sourceText =
                new String(Files.readAllBytes(repairCase.source()), StandardCharsets.UTF_8);
        String patchedText =
                new String(Files.readAllBytes(repairCase.patched()), StandardCharsets.UTF_8);
        for (String fragment : repairCase.expectedPatchFragments()) {
            assertTrue(
                    repairCase.name() + " expected fragment " + fragment,
                    patchedText.contains(fragment));
        }

        TypecheckResult after =
                compileNullness(repairCase.patched().getParent(), repairCase.patched());
        Outcome outcome = outcome(sourceText, patchedText, after);
        switch (repairCase.validationMode()) {
            case MUST_PASS:
                assertTrue(repairCase.name(), after.getCompilationResult().compiledWithoutError());
                assertTrue(repairCase.name(), outcome == Outcome.PATCHED_AND_PASSED);
                break;
            case MUST_DECREASE:
                assertTrue(
                        repairCase.name(),
                        after.getActualDiagnostics().size() < before.getActualDiagnostics().size());
                break;
            case SKETCH_ONLY:
                assertTrue(repairCase.name(), sourceText.equals(patchedText));
                assertTrue(repairCase.name(), outcome == Outcome.SKETCH_ONLY);
                break;
            default:
                throw new AssertionError(
                        "Unhandled validation mode " + repairCase.validationMode());
        }
        return new RepairResult(
                before.getActualDiagnostics().size(), after.getActualDiagnostics().size(), outcome);
    }

    private void runAgentRepair(
            RepairCase repairCase,
            TraceModel.DiagnosticSlice slice,
            List<SuggestedRepair> deterministicRepairs)
            throws Exception {
        Path context = repairCase.patched().resolveSibling(repairCase.name() + ".context.jsonl");
        Path proposal = repairCase.patched().resolveSibling(repairCase.name() + ".proposal.jsonl");
        Path plan = repairCase.patched().resolveSibling(repairCase.name() + ".plan.jsonl");
        Path validationReport =
                repairCase.patched().resolveSibling(repairCase.name() + ".validation.jsonl");
        Path finalContext =
                repairCase.patched().resolveSibling(repairCase.name() + ".final-context.jsonl");

        String diagnosticId = slice.diagnostic().id;
        String contextJson =
                new AgentContextJson()
                        .toJson(
                                repairCase.source(),
                                diagnosticId,
                                slice,
                                deterministicRepairs,
                                Collections.emptyList(),
                                null);
        Files.write(context, Collections.singletonList(contextJson), StandardCharsets.UTF_8);
        TraceEvent contextEvent = new AgentContextParser().parse(context);

        SuggestedRepair agentRepair = firstAutomaticEditRepair(repairCase, deterministicRepairs);
        Files.write(
                proposal,
                Collections.singletonList(agentProposalJson(diagnosticId, agentRepair)),
                StandardCharsets.UTF_8);
        List<PlannedRepair> plannedRepairs =
                new AgentProposalParser().parse(proposal, contextEvent);
        List<String> planLines = new ArrayList<>();
        checker_reconcile.repair.RepairPlanJson planJson =
                new checker_reconcile.repair.RepairPlanJson();
        for (PlannedRepair plannedRepair : plannedRepairs) {
            planLines.add(planJson.toJson(plannedRepair));
        }
        Files.write(plan, planLines, StandardCharsets.UTF_8);

        new Patcher()
                .writePlanned(repairCase.source(), repairCase.patched(), repairs(plannedRepairs));
        Validation.Result after =
                new Validation().validateDetailed(javac(), checkerAllJar(), repairCase.patched());
        boolean accepted = after.exitCode() == 0;
        Files.write(
                validationReport,
                Collections.singletonList(
                        new ValidationReportJson()
                                .toJson(
                                        repairCase.source(),
                                        repairCase.patched(),
                                        "pass",
                                        accepted,
                                        null,
                                        after,
                                        plannedRepairs)),
                StandardCharsets.UTF_8);
        TraceEvent validationResult = new ValidationReportParser().parse(validationReport);
        Files.write(
                finalContext,
                Collections.singletonList(
                        new AgentContextJson()
                                .toJson(
                                        repairCase.source(),
                                        diagnosticId,
                                        slice,
                                        deterministicRepairs,
                                        Collections.emptyList(),
                                        validationResult)),
                StandardCharsets.UTF_8);
        new AgentContextParser().parse(finalContext);
        assertTrue(repairCase.name() + " agent validation accepted", accepted);
    }

    private SuggestedRepair firstAutomaticEditRepair(
            RepairCase repairCase, List<SuggestedRepair> repairs) {
        for (SuggestedRepair repair : repairs) {
            if (repair.automatic() && !repair.edits().isEmpty()) {
                return repair;
            }
        }
        throw new AssertionError(repairCase.name() + " has no automatic edit repair");
    }

    private List<SuggestedRepair> repairs(List<PlannedRepair> plannedRepairs) {
        List<SuggestedRepair> repairs = new ArrayList<>();
        for (PlannedRepair plannedRepair : plannedRepairs) {
            repairs.add(plannedRepair.repair());
        }
        return repairs;
    }

    private String agentProposalJson(String diagnosticId, SuggestedRepair repair) {
        StringBuilder result = new StringBuilder("{");
        result.append("\"schema_version\":1,");
        field(result, "event", "agent_proposal");
        result.append(',');
        field(result, "diagnostic_id", diagnosticId);
        result.append(',');
        field(result, "kind", repair.kind().name());
        result.append(',');
        field(result, "risk", repair.risk().name());
        result.append(',');
        result.append("\"automatic\":").append(repair.automatic());
        result.append(',');
        result.append("\"confidence\":1.0,");
        result.append("\"requires_validation\":true,");
        field(result, "message", repair.message());
        result.append(',');
        stringArrayField(result, "evidence_ids", repair.evidenceIds());
        result.append(',');
        editsField(result, repair.edits());
        return result.append('}').toString();
    }

    private void editsField(StringBuilder result, List<SourceEdit> edits) {
        result.append("\"edits\":[");
        for (int i = 0; i < edits.size(); i++) {
            if (i > 0) {
                result.append(',');
            }
            SourceEdit edit = edits.get(i);
            result.append('{');
            result.append("\"start_offset\":").append(edit.startOffset());
            result.append(',');
            result.append("\"end_offset\":").append(edit.endOffset());
            result.append(',');
            field(result, "replacement", edit.replacement());
            result.append('}');
        }
        result.append(']');
    }

    private void stringArrayField(StringBuilder result, String name, List<String> values) {
        result.append('"').append(name).append("\":[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append(',');
            }
            result.append(quote(values.get(i)));
        }
        result.append(']');
    }

    private void field(StringBuilder result, String name, String value) {
        result.append('"').append(name).append("\":").append(quote(value));
    }

    private String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\b':
                    result.append("\\b");
                    break;
                case '\f':
                    result.append("\\f");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        result.append(String.format("\\u%04x", (int) ch));
                    } else {
                        result.append(ch);
                    }
                    break;
            }
        }
        return result.append('"').toString();
    }

    private ValidationBackedRepairSearch.Listener searchReportListener(List<String> lines) {
        SearchReportJson json = new SearchReportJson();
        return new ValidationBackedRepairSearch.Listener() {
            @Override
            public void skipped(int index, RepairCandidateSet candidateSet, String reason) {
                lines.add(json.skipped(index, candidateSet, reason));
            }

            @Override
            public void validated(
                    int index,
                    RepairCandidateSet candidateSet,
                    checker_reconcile.repair.Validation.Result after,
                    boolean accepted) {
                lines.add(json.validated(index, candidateSet, after, accepted));
            }

            @Override
            public void invalid(int index, RepairCandidateSet candidateSet, String reason) {
                lines.add(json.invalid(index, candidateSet, reason));
            }

            @Override
            public void pruned(int index, RepairCandidateSet candidateSet, String reason) {
                lines.add(json.pruned(index, candidateSet, reason));
            }
        };
    }

    private void assertExpectedSearchReport(RepairCase repairCase, Path report) throws Exception {
        assertTrue(repairCase.name() + " search report exists", Files.isRegularFile(report));
        List<TraceEvent> events = new SearchReportParser().parse(report);
        assertFalse(repairCase.name() + " search report events", events.isEmpty());
        String reportText = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);
        TraceEvent summary = events.get(events.size() - 1);
        assertTrue(repairCase.name() + " search report summary", "summary".equals(summary.event));
        if (repairCase.expectedGeneratedCandidateCount() != null) {
            assertTrue(
                    repairCase.name() + " generated candidate count",
                    ((Number) summary.fields.get("generated_candidate_count")).intValue()
                            == repairCase.expectedGeneratedCandidateCount());
        }
        if (repairCase.expectedSearchedCandidateCount() != null) {
            assertTrue(
                    repairCase.name() + " searched candidate count",
                    ((Number) summary.fields.get("searched_candidate_count")).intValue()
                            == repairCase.expectedSearchedCandidateCount());
        }
        if (repairCase.expectedPrunedEmptyEditCount() != null) {
            assertTrue(
                    repairCase.name() + " pruned empty edit count",
                    ((Number) summary.fields.get("pruned_empty_edit_count")).intValue()
                            == repairCase.expectedPrunedEmptyEditCount());
        }
        if (repairCase.expectedPrunedDuplicateEditCount() != null) {
            assertTrue(
                    repairCase.name() + " pruned duplicate edit count",
                    ((Number) summary.fields.get("pruned_duplicate_edit_count")).intValue()
                            == repairCase.expectedPrunedDuplicateEditCount());
        }
        if (repairCase.expectedPrunedOverlapCount() != null) {
            assertTrue(
                    repairCase.name() + " pruned overlap count",
                    ((Number) summary.fields.get("pruned_overlap_count")).intValue()
                            == repairCase.expectedPrunedOverlapCount());
        }
        if (!repairCase.expectedPrunedEmptyEditReasons().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> reasons =
                    (Map<String, Object>) summary.fields.get("pruned_empty_edit_reasons");
            for (RepairCase.ExpectedCount expected : repairCase.expectedPrunedEmptyEditReasons()) {
                Object count = reasons.get(expected.key());
                assertTrue(
                        repairCase.name()
                                + " pruned empty edit reason "
                                + expected.key()
                                + "="
                                + expected.count(),
                        count instanceof Number && ((Number) count).intValue() == expected.count());
            }
        }
        if (repairCase.expectedPrunedBudgetCount() != null) {
            assertTrue(
                    repairCase.name() + " pruned budget count",
                    ((Number) summary.fields.get("pruned_budget_count")).intValue()
                            == repairCase.expectedPrunedBudgetCount());
        }
        if (!repairCase.expectedSearchDiagnosticIds().isEmpty()) {
            assertTrue(
                    repairCase.name() + " summary accepted diagnostic ids",
                    summary.listField("accepted_diagnostic_ids")
                            .containsAll(repairCase.expectedSearchDiagnosticIds()));
            assertTrue(
                    repairCase.name() + " accepted candidate diagnostic ids",
                    events.stream()
                            .anyMatch(
                                    event ->
                                            "candidate_validated".equals(event.event)
                                                    && Boolean.TRUE.equals(
                                                            event.fields.get("accepted"))
                                                    && event.listField("diagnostic_ids")
                                                            .containsAll(
                                                                    repairCase
                                                                            .expectedSearchDiagnosticIds())));
        }
        for (String expectedEvent : repairCase.expectedSearchEvents()) {
            assertTrue(
                    repairCase.name() + " expected search event " + expectedEvent,
                    events.stream().anyMatch(event -> expectedEvent.equals(event.event)));
        }
        for (String fragment : repairCase.expectedSearchReportFragments()) {
            assertTrue(
                    repairCase.name() + " expected search report fragment " + fragment,
                    reportText.contains(fragment));
        }
    }

    private void assertExpectedRepairs(RepairCase repairCase, List<SuggestedRepair> repairs) {
        if (repairCase.expectedRepairKinds().isEmpty()
                && repairCase.expectedRepairRisk() == null
                && repairCase.expectedRepairAutomatic() == null
                && repairCase.expectedRepairEditCount() == null) {
            return;
        }
        if (repairCase.expectedRepairKinds().isEmpty()) {
            assertTrue(
                    repairCase.name() + " expected a repair matching plan constraints",
                    repairs.stream()
                            .anyMatch(repair -> matchesPlanConstraints(repairCase, repair)));
            return;
        }
        for (String repairKind : repairCase.expectedRepairKinds()) {
            assertTrue(
                    repairCase.name()
                            + " expected repair kind "
                            + repairKind
                            + " matching plan constraints",
                    repairs.stream()
                            .anyMatch(
                                    repair ->
                                            repair.kind().name().equals(repairKind)
                                                    && matchesPlanConstraints(repairCase, repair)));
        }
    }

    private boolean matchesPlanConstraints(RepairCase repairCase, SuggestedRepair repair) {
        if (repairCase.expectedRepairRisk() != null
                && !repair.risk().name().equals(repairCase.expectedRepairRisk())) {
            return false;
        }
        if (repairCase.expectedRepairAutomatic() != null
                && repair.automatic() != repairCase.expectedRepairAutomatic()) {
            return false;
        }
        return repairCase.expectedRepairEditCount() == null
                || repair.edits().size() == repairCase.expectedRepairEditCount();
    }

    private Outcome outcome(String sourceText, String patchedText, TypecheckResult after) {
        if (sourceText.equals(patchedText)) {
            return Outcome.SKETCH_ONLY;
        }
        if (after.getCompilationResult().compiledWithoutError()) {
            return Outcome.PATCHED_AND_PASSED;
        }
        return Outcome.PATCHED_BUT_FAILED;
    }

    private TraceModel.DiagnosticSlice firstSliceWithKind(TraceModel model, String expectedKind) {
        for (String diagnosticId : model.diagnostics.keySet()) {
            TraceModel.DiagnosticSlice slice = model.slice(diagnosticId);
            if (expectedKind.equals(slice.diagnostic().stringField("error_kind"))) {
                return slice;
            }
        }
        throw new AssertionError("no trace diagnostic with kind " + expectedKind);
    }

    private String javac() {
        return Paths.get(System.getProperty("java.home"), "bin", "javac").toString();
    }

    private Path checkerAllJar() throws Exception {
        Path libs = checkerLibs();
        try (Stream<Path> jars = Files.list(libs)) {
            return jars.filter(path -> path.getFileName().toString().endsWith("-all.jar"))
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElseThrow(
                            () ->
                                    new AssertionError(
                                            "missing Checker Framework all jar under " + libs));
        }
    }

    private Path checkerLibs() {
        Path moduleLibs = Paths.get("build", "libs");
        if (Files.isDirectory(moduleLibs)) {
            return moduleLibs;
        }
        Path repoLibs = Paths.get("checker", "build", "libs");
        if (Files.isDirectory(repoLibs)) {
            return repoLibs;
        }
        throw new AssertionError("missing Checker Framework all jar directory");
    }

    public TypecheckResult compileNullness(Path testDir, Path source, String... extraOptions) {
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

    /** Before/after diagnostic counts from one repair run. */
    public static final class RepairResult {
        private final int beforeDiagnosticCount;
        private final int afterDiagnosticCount;
        private final Outcome outcome;

        public RepairResult(int beforeDiagnosticCount, int afterDiagnosticCount, Outcome outcome) {
            this.beforeDiagnosticCount = beforeDiagnosticCount;
            this.afterDiagnosticCount = afterDiagnosticCount;
            this.outcome = outcome;
        }

        public int beforeDiagnosticCount() {
            return beforeDiagnosticCount;
        }

        public int afterDiagnosticCount() {
            return afterDiagnosticCount;
        }

        public Outcome outcome() {
            return outcome;
        }
    }

    /** Coarse repair result classification. */
    public enum Outcome {
        PATCHED_AND_PASSED,
        PATCHED_BUT_FAILED,
        SKETCH_ONLY
    }
}
