package org.checkerframework.checker.test.junit.repair;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import checker_reconcile.diagnosis.ValidationBackedRepairSearch;
import checker_reconcile.repair.RiskLevel;

/** One strict repair regression case. */
public final class RepairCase {
    private final String name;
    private final Path source;
    private final Path trace;
    private final Path patched;
    private final String expectedDiagnosticKind;
    private final List<String> expectedPatchFragments;
    private final List<String> expectedRepairKinds;
    private final String expectedRepairRisk;
    private final Boolean expectedRepairAutomatic;
    private final Integer expectedRepairEditCount;
    private final ValidationMode validationMode;
    private final Runner runner;
    private final String searchValidationMode;
    private final RiskLevel allowedRisk;
    private final boolean expectedSearchAccepted;
    private final Integer expectedSearchCandidateSize;
    private final int maxCandidateSize;
    private final int maxSearchCandidates;
    private final boolean includeSketchEdits;
    private final boolean expectSearchReport;
    private final List<String> expectedSearchDiagnosticIds;
    private final Integer expectedGeneratedCandidateCount;
    private final Integer expectedSearchedCandidateCount;
    private final Integer expectedPrunedEmptyEditCount;
    private final Integer expectedPrunedDuplicateEditCount;
    private final Integer expectedPrunedOverlapCount;
    private final Integer expectedPrunedBudgetCount;
    private final List<ExpectedCount> expectedPrunedEmptyEditReasons;
    private final List<String> expectedSearchEvents;
    private final List<String> expectedSearchReportFragments;

    public RepairCase(
            String name,
            Path source,
            Path trace,
            Path patched,
            String expectedDiagnosticKind,
            List<String> expectedPatchFragments,
            ValidationMode validationMode) {
        this(
                name,
                source,
                trace,
                patched,
                expectedDiagnosticKind,
                expectedPatchFragments,
                Collections.emptyList(),
                null,
                null,
                null,
                validationMode,
                Runner.PATCH,
                null,
                null,
                true,
                null,
                2,
                ValidationBackedRepairSearch.DEFAULT_MAX_SEARCH_CANDIDATES,
                false,
                false,
                Collections.emptyList(),
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    public RepairCase(
            String name,
            Path source,
            Path trace,
            Path patched,
            String expectedDiagnosticKind,
            List<String> expectedPatchFragments,
            List<String> expectedRepairKinds,
            ValidationMode validationMode) {
        this(
                name,
                source,
                trace,
                patched,
                expectedDiagnosticKind,
                expectedPatchFragments,
                expectedRepairKinds,
                null,
                null,
                null,
                validationMode,
                Runner.PATCH,
                null,
                null,
                true,
                null,
                2,
                ValidationBackedRepairSearch.DEFAULT_MAX_SEARCH_CANDIDATES,
                false,
                false,
                Collections.emptyList(),
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    public RepairCase(
            String name,
            Path source,
            Path trace,
            Path patched,
            String expectedDiagnosticKind,
            List<String> expectedPatchFragments,
            List<String> expectedRepairKinds,
            String expectedRepairRisk,
            Boolean expectedRepairAutomatic,
            Integer expectedRepairEditCount,
            ValidationMode validationMode) {
        this(
                name,
                source,
                trace,
                patched,
                expectedDiagnosticKind,
                expectedPatchFragments,
                expectedRepairKinds,
                expectedRepairRisk,
                expectedRepairAutomatic,
                expectedRepairEditCount,
                validationMode,
                Runner.PATCH,
                null,
                null,
                true,
                null,
                2,
                ValidationBackedRepairSearch.DEFAULT_MAX_SEARCH_CANDIDATES,
                false,
                false,
                Collections.emptyList(),
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    public RepairCase(
            String name,
            Path source,
            Path trace,
            Path patched,
            String expectedDiagnosticKind,
            List<String> expectedPatchFragments,
            List<String> expectedRepairKinds,
            String expectedRepairRisk,
            Boolean expectedRepairAutomatic,
            Integer expectedRepairEditCount,
            ValidationMode validationMode,
            Runner runner,
            String searchValidationMode,
            RiskLevel allowedRisk,
            boolean expectedSearchAccepted) {
        this(
                name,
                source,
                trace,
                patched,
                expectedDiagnosticKind,
                expectedPatchFragments,
                expectedRepairKinds,
                expectedRepairRisk,
                expectedRepairAutomatic,
                expectedRepairEditCount,
                validationMode,
                runner,
                searchValidationMode,
                allowedRisk,
                expectedSearchAccepted,
                null,
                2,
                ValidationBackedRepairSearch.DEFAULT_MAX_SEARCH_CANDIDATES,
                false,
                false,
                Collections.emptyList(),
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    public RepairCase(
            String name,
            Path source,
            Path trace,
            Path patched,
            String expectedDiagnosticKind,
            List<String> expectedPatchFragments,
            List<String> expectedRepairKinds,
            String expectedRepairRisk,
            Boolean expectedRepairAutomatic,
            Integer expectedRepairEditCount,
            ValidationMode validationMode,
            Runner runner,
            String searchValidationMode,
            RiskLevel allowedRisk,
            boolean expectedSearchAccepted,
            Integer expectedSearchCandidateSize,
            int maxCandidateSize,
            int maxSearchCandidates,
            boolean includeSketchEdits,
            boolean expectSearchReport,
            List<String> expectedSearchDiagnosticIds,
            Integer expectedGeneratedCandidateCount,
            Integer expectedSearchedCandidateCount,
            Integer expectedPrunedEmptyEditCount,
            Integer expectedPrunedDuplicateEditCount,
            Integer expectedPrunedOverlapCount,
            Integer expectedPrunedBudgetCount,
            List<ExpectedCount> expectedPrunedEmptyEditReasons,
            List<String> expectedSearchEvents,
            List<String> expectedSearchReportFragments) {
        this.name = name;
        this.source = source;
        this.trace = trace;
        this.patched = patched;
        this.expectedDiagnosticKind = expectedDiagnosticKind;
        this.expectedPatchFragments = Collections.unmodifiableList(expectedPatchFragments);
        this.expectedRepairKinds = Collections.unmodifiableList(expectedRepairKinds);
        this.expectedRepairRisk = expectedRepairRisk;
        this.expectedRepairAutomatic = expectedRepairAutomatic;
        this.expectedRepairEditCount = expectedRepairEditCount;
        this.validationMode = validationMode;
        this.runner = runner;
        this.searchValidationMode = searchValidationMode;
        this.allowedRisk = allowedRisk;
        this.expectedSearchAccepted = expectedSearchAccepted;
        this.expectedSearchCandidateSize = expectedSearchCandidateSize;
        this.maxCandidateSize = maxCandidateSize;
        this.maxSearchCandidates = maxSearchCandidates;
        this.includeSketchEdits = includeSketchEdits;
        this.expectSearchReport = expectSearchReport;
        this.expectedSearchDiagnosticIds =
                Collections.unmodifiableList(expectedSearchDiagnosticIds);
        this.expectedGeneratedCandidateCount = expectedGeneratedCandidateCount;
        this.expectedSearchedCandidateCount = expectedSearchedCandidateCount;
        this.expectedPrunedEmptyEditCount = expectedPrunedEmptyEditCount;
        this.expectedPrunedDuplicateEditCount = expectedPrunedDuplicateEditCount;
        this.expectedPrunedOverlapCount = expectedPrunedOverlapCount;
        this.expectedPrunedBudgetCount = expectedPrunedBudgetCount;
        this.expectedPrunedEmptyEditReasons =
                Collections.unmodifiableList(expectedPrunedEmptyEditReasons);
        this.expectedSearchEvents = Collections.unmodifiableList(expectedSearchEvents);
        this.expectedSearchReportFragments =
                Collections.unmodifiableList(expectedSearchReportFragments);
    }

    public String name() {
        return name;
    }

    public Path source() {
        return source;
    }

    public Path trace() {
        return trace;
    }

    public Path patched() {
        return patched;
    }

    public String expectedDiagnosticKind() {
        return expectedDiagnosticKind;
    }

    public List<String> expectedPatchFragments() {
        return expectedPatchFragments;
    }

    public List<String> expectedRepairKinds() {
        return expectedRepairKinds;
    }

    public String expectedRepairRisk() {
        return expectedRepairRisk;
    }

    public Boolean expectedRepairAutomatic() {
        return expectedRepairAutomatic;
    }

    public Integer expectedRepairEditCount() {
        return expectedRepairEditCount;
    }

    public ValidationMode validationMode() {
        return validationMode;
    }

    public Runner runner() {
        return runner;
    }

    public String searchValidationMode() {
        return searchValidationMode;
    }

    public RiskLevel allowedRisk() {
        return allowedRisk;
    }

    public boolean expectedSearchAccepted() {
        return expectedSearchAccepted;
    }

    public Integer expectedSearchCandidateSize() {
        return expectedSearchCandidateSize;
    }

    public int maxCandidateSize() {
        return maxCandidateSize;
    }

    public int maxSearchCandidates() {
        return maxSearchCandidates;
    }

    public boolean includeSketchEdits() {
        return includeSketchEdits;
    }

    public boolean expectSearchReport() {
        return expectSearchReport;
    }

    public List<String> expectedSearchDiagnosticIds() {
        return expectedSearchDiagnosticIds;
    }

    public Integer expectedGeneratedCandidateCount() {
        return expectedGeneratedCandidateCount;
    }

    public Integer expectedSearchedCandidateCount() {
        return expectedSearchedCandidateCount;
    }

    public Integer expectedPrunedEmptyEditCount() {
        return expectedPrunedEmptyEditCount;
    }

    public Integer expectedPrunedDuplicateEditCount() {
        return expectedPrunedDuplicateEditCount;
    }

    public Integer expectedPrunedOverlapCount() {
        return expectedPrunedOverlapCount;
    }

    public Integer expectedPrunedBudgetCount() {
        return expectedPrunedBudgetCount;
    }

    public List<ExpectedCount> expectedPrunedEmptyEditReasons() {
        return expectedPrunedEmptyEditReasons;
    }

    public List<String> expectedSearchEvents() {
        return expectedSearchEvents;
    }

    public List<String> expectedSearchReportFragments() {
        return expectedSearchReportFragments;
    }

    /** Validation acceptance mode. */
    public enum ValidationMode {
        MUST_PASS,
        MUST_DECREASE,
        SKETCH_ONLY
    }

    /** Repair execution path used by a regression case. */
    public enum Runner {
        PATCH,
        SEARCH,
        AGENT
    }

    /** Expected integer value associated with a string key. */
    public static final class ExpectedCount {
        private final String key;
        private final int count;

        public ExpectedCount(String key, int count) {
            this.key = key;
            this.count = count;
        }

        public String key() {
            return key;
        }

        public int count() {
            return count;
        }
    }
}
