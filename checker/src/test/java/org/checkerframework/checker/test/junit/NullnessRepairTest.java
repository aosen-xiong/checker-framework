package org.checkerframework.checker.test.junit;

import org.checkerframework.checker.nullness.NullnessChecker;
import org.checkerframework.checker.test.junit.repair.RepairCase;
import org.checkerframework.checker.test.junit.repair.RepairHarness;
import org.checkerframework.framework.test.CheckerFrameworkPerFileTest;
import org.checkerframework.framework.test.TestUtilities;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import checker_reconcile.repair.RiskLevel;

/** File-based end-to-end repair tests for the Nullness Checker. */
public class NullnessRepairTest extends CheckerFrameworkPerFileTest {

    /** Create a NullnessRepairTest. */
    public NullnessRepairTest(File testFile) {
        super(testFile, NullnessChecker.class, "nullness-repair");
    }

    @Parameters
    public static List<File> getTestFiles() {
        return TestUtilities.findNestedJavaTestFiles("nullness-repair");
    }

    @Override
    @Test
    public void run() {
        try {
            RepairMetadata metadata = RepairMetadata.parse(testFile.toPath());
            Path testDir = Files.createTempDirectory("nullness-repair-file-test");
            Path trace = testDir.resolve("trace.jsonl");
            Path patched = testDir.resolve(testFile.getName());

            new RepairHarness()
                    .run(
                            new RepairCase(
                                    testFile.getName(),
                                    testFile.toPath(),
                                    trace,
                                    patched,
                                    metadata.expectedDiagnosticKind,
                                    metadata.expectedPatchFragments,
                                    metadata.expectedRepairKinds,
                                    metadata.expectedRepairRisk,
                                    metadata.expectedRepairAutomatic,
                                    metadata.expectedRepairEditCount,
                                    metadata.validationMode,
                                    metadata.runner,
                                    metadata.searchValidationMode,
                                    metadata.allowedRisk,
                                    metadata.expectedSearchAccepted,
                                    metadata.expectedSearchCandidateSize,
                                    metadata.maxCandidateSize,
                                    metadata.maxSearchCandidates,
                                    metadata.includeSketchEdits,
                                    metadata.expectSearchReport,
                                    metadata.expectedSearchDiagnosticIds,
                                    metadata.expectedGeneratedCandidateCount,
                                    metadata.expectedSearchedCandidateCount,
                                    metadata.expectedPrunedEmptyEditCount,
                                    metadata.expectedPrunedDuplicateEditCount,
                                    metadata.expectedPrunedOverlapCount,
                                    metadata.expectedPrunedBudgetCount,
                                    metadata.expectedPrunedEmptyEditReasons,
                                    metadata.expectedSearchEvents,
                                    metadata.expectedSearchReportFragments));
        } catch (Exception e) {
            throw new AssertionError("repair test failed for " + testFile, e);
        }
    }

    /** Metadata parsed from comments in a repair test source file. */
    private static final class RepairMetadata {
        private static final String KIND_PREFIX = "// @repair-kind:";
        private static final String MODE_PREFIX = "// @repair-mode:";
        private static final String RUNNER_PREFIX = "// @repair-runner:";
        private static final String SEARCH_MODE_PREFIX = "// @repair-search-mode:";
        private static final String SEARCH_ACCEPTED_PREFIX = "// @repair-search-accepted:";
        private static final String SEARCH_CANDIDATE_SIZE_PREFIX =
                "// @repair-search-candidate-size:";
        private static final String SEARCH_REPORT_PREFIX = "// @repair-search-report:";
        private static final String SEARCH_DIAGNOSTIC_IDS_PREFIX =
                "// @repair-search-diagnostic-ids:";
        private static final String SEARCH_GENERATED_CANDIDATES_PREFIX =
                "// @repair-search-generated-candidates:";
        private static final String SEARCH_SEARCHED_CANDIDATES_PREFIX =
                "// @repair-search-searched-candidates:";
        private static final String SEARCH_PRUNED_EMPTY_PREFIX = "// @repair-search-pruned-empty:";
        private static final String SEARCH_PRUNED_EMPTY_REASON_PREFIX =
                "// @repair-search-pruned-empty-reason:";
        private static final String SEARCH_PRUNED_DUPLICATE_PREFIX =
                "// @repair-search-pruned-duplicate:";
        private static final String SEARCH_PRUNED_OVERLAP_PREFIX =
                "// @repair-search-pruned-overlap:";
        private static final String SEARCH_PRUNED_BUDGET_PREFIX =
                "// @repair-search-pruned-budget:";
        private static final String SEARCH_EVENT_PREFIX = "// @repair-search-event:";
        private static final String SEARCH_REPORT_CONTAINS_PREFIX =
                "// @repair-search-report-contains:";
        private static final String MAX_CANDIDATE_SIZE_PREFIX = "// @repair-max-candidate-size:";
        private static final String MAX_SEARCH_CANDIDATES_PREFIX =
                "// @repair-max-search-candidates:";
        private static final String INCLUDE_SKETCH_EDITS_PREFIX =
                "// @repair-include-sketch-edits:";
        private static final String ALLOW_RISK_PREFIX = "// @repair-allow-risk:";
        private static final String CONTAINS_PREFIX = "// @repair-contains:";
        private static final String CONTAINS_RAW_PREFIX = "// @repair-contains-raw:";
        private static final String REPAIR_KIND_PREFIX = "// @repair-plan-kind:";
        private static final String REPAIR_RISK_PREFIX = "// @repair-plan-risk:";
        private static final String REPAIR_AUTOMATIC_PREFIX = "// @repair-plan-automatic:";
        private static final String REPAIR_EDITS_PREFIX = "// @repair-plan-edits:";

        private final String expectedDiagnosticKind;
        private final RepairCase.ValidationMode validationMode;
        private final List<String> expectedPatchFragments;
        private final List<String> expectedRepairKinds;
        private final String expectedRepairRisk;
        private final Boolean expectedRepairAutomatic;
        private final Integer expectedRepairEditCount;
        private final RepairCase.Runner runner;
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
        private final List<RepairCase.ExpectedCount> expectedPrunedEmptyEditReasons;
        private final List<String> expectedSearchEvents;
        private final List<String> expectedSearchReportFragments;

        private RepairMetadata(
                String expectedDiagnosticKind,
                RepairCase.ValidationMode validationMode,
                List<String> expectedPatchFragments,
                List<String> expectedRepairKinds,
                String expectedRepairRisk,
                Boolean expectedRepairAutomatic,
                Integer expectedRepairEditCount,
                RepairCase.Runner runner,
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
                List<RepairCase.ExpectedCount> expectedPrunedEmptyEditReasons,
                List<String> expectedSearchEvents,
                List<String> expectedSearchReportFragments) {
            this.expectedDiagnosticKind = expectedDiagnosticKind;
            this.validationMode = validationMode;
            this.expectedPatchFragments = expectedPatchFragments;
            this.expectedRepairKinds = expectedRepairKinds;
            this.expectedRepairRisk = expectedRepairRisk;
            this.expectedRepairAutomatic = expectedRepairAutomatic;
            this.expectedRepairEditCount = expectedRepairEditCount;
            this.runner = runner;
            this.searchValidationMode = searchValidationMode;
            this.allowedRisk = allowedRisk;
            this.expectedSearchAccepted = expectedSearchAccepted;
            this.expectedSearchCandidateSize = expectedSearchCandidateSize;
            this.maxCandidateSize = maxCandidateSize;
            this.maxSearchCandidates = maxSearchCandidates;
            this.includeSketchEdits = includeSketchEdits;
            this.expectSearchReport = expectSearchReport;
            this.expectedSearchDiagnosticIds = expectedSearchDiagnosticIds;
            this.expectedGeneratedCandidateCount = expectedGeneratedCandidateCount;
            this.expectedSearchedCandidateCount = expectedSearchedCandidateCount;
            this.expectedPrunedEmptyEditCount = expectedPrunedEmptyEditCount;
            this.expectedPrunedDuplicateEditCount = expectedPrunedDuplicateEditCount;
            this.expectedPrunedOverlapCount = expectedPrunedOverlapCount;
            this.expectedPrunedBudgetCount = expectedPrunedBudgetCount;
            this.expectedPrunedEmptyEditReasons = expectedPrunedEmptyEditReasons;
            this.expectedSearchEvents = expectedSearchEvents;
            this.expectedSearchReportFragments = expectedSearchReportFragments;
        }

        private static RepairMetadata parse(Path source) throws IOException {
            RepairMetadataBuilder builder = new RepairMetadataBuilder();
            List<MetadataDirective> directives = directives(source, builder);

            for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                applyDirective(directives, trimmed);
            }

            return builder.build(source);
        }

        private static List<MetadataDirective> directives(
                Path source, RepairMetadataBuilder builder) {
            List<MetadataDirective> result = new ArrayList<>();
            result.add(
                    new MetadataDirective(
                            KIND_PREFIX, value -> builder.expectedDiagnosticKind = value.trim()));
            result.add(
                    new MetadataDirective(
                            MODE_PREFIX,
                            value -> builder.validationMode = parseValidationMode(value)));
            result.add(
                    new MetadataDirective(
                            RUNNER_PREFIX, value -> builder.runner = parseRunner(value)));
            result.add(
                    new MetadataDirective(
                            SEARCH_MODE_PREFIX,
                            value ->
                                    builder.searchValidationMode = parseSearchMode(source, value)));
            result.add(
                    new MetadataDirective(
                            SEARCH_ACCEPTED_PREFIX,
                            value ->
                                    builder.expectedSearchAccepted =
                                            Boolean.valueOf(value.trim())));
            result.add(
                    new MetadataDirective(
                            SEARCH_CANDIDATE_SIZE_PREFIX,
                            value ->
                                    builder.expectedSearchCandidateSize =
                                            Integer.valueOf(value.trim())));
            result.add(
                    new MetadataDirective(
                            SEARCH_REPORT_PREFIX,
                            value -> builder.expectSearchReport = Boolean.valueOf(value.trim())));
            result.add(
                    new MetadataDirective(
                            SEARCH_DIAGNOSTIC_IDS_PREFIX,
                            value -> builder.expectedSearchDiagnosticIds = parseList(value)));
            result.add(
                    new MetadataDirective(
                            SEARCH_GENERATED_CANDIDATES_PREFIX,
                            value ->
                                    builder.expectedGeneratedCandidateCount =
                                            Integer.valueOf(value.trim())));
            result.add(
                    new MetadataDirective(
                            SEARCH_SEARCHED_CANDIDATES_PREFIX,
                            value ->
                                    builder.expectedSearchedCandidateCount =
                                            Integer.valueOf(value.trim())));
            result.add(
                    new MetadataDirective(
                            SEARCH_PRUNED_EMPTY_PREFIX,
                            value ->
                                    builder.expectedPrunedEmptyEditCount =
                                            Integer.valueOf(value.trim())));
            result.add(
                    new MetadataDirective(
                            SEARCH_PRUNED_EMPTY_REASON_PREFIX,
                            value ->
                                    builder.expectedPrunedEmptyEditReasons.add(
                                            parseExpectedCount(source, value))));
            result.add(
                    new MetadataDirective(
                            SEARCH_PRUNED_DUPLICATE_PREFIX,
                            value ->
                                    builder.expectedPrunedDuplicateEditCount =
                                            Integer.valueOf(value.trim())));
            result.add(
                    new MetadataDirective(
                            SEARCH_PRUNED_OVERLAP_PREFIX,
                            value ->
                                    builder.expectedPrunedOverlapCount =
                                            Integer.valueOf(value.trim())));
            result.add(
                    new MetadataDirective(
                            SEARCH_PRUNED_BUDGET_PREFIX,
                            value ->
                                    builder.expectedPrunedBudgetCount =
                                            Integer.valueOf(value.trim())));
            result.add(
                    new MetadataDirective(
                            SEARCH_EVENT_PREFIX,
                            value -> builder.expectedSearchEvents.add(value.trim())));
            result.add(
                    new MetadataDirective(
                            SEARCH_REPORT_CONTAINS_PREFIX,
                            value -> builder.expectedSearchReportFragments.add(value.trim())));
            result.add(
                    new MetadataDirective(
                            MAX_CANDIDATE_SIZE_PREFIX,
                            value -> builder.maxCandidateSize = Integer.parseInt(value.trim())));
            result.add(
                    new MetadataDirective(
                            MAX_SEARCH_CANDIDATES_PREFIX,
                            value -> builder.maxSearchCandidates = Integer.parseInt(value.trim())));
            result.add(
                    new MetadataDirective(
                            INCLUDE_SKETCH_EDITS_PREFIX,
                            value -> builder.includeSketchEdits = Boolean.valueOf(value.trim())));
            result.add(
                    new MetadataDirective(
                            ALLOW_RISK_PREFIX,
                            value -> builder.allowedRisk = RiskLevel.valueOf(value.trim())));
            result.add(
                    new MetadataDirective(
                            CONTAINS_PREFIX,
                            value -> builder.expectedPatchFragments.add(value.trim())));
            result.add(
                    new MetadataDirective(
                            CONTAINS_RAW_PREFIX,
                            value -> builder.expectedPatchFragments.add(value)));
            result.add(
                    new MetadataDirective(
                            REPAIR_KIND_PREFIX,
                            value -> builder.expectedRepairKinds.add(value.trim())));
            result.add(
                    new MetadataDirective(
                            REPAIR_RISK_PREFIX,
                            value -> builder.expectedRepairRisk = value.trim()));
            result.add(
                    new MetadataDirective(
                            REPAIR_AUTOMATIC_PREFIX,
                            value ->
                                    builder.expectedRepairAutomatic =
                                            Boolean.valueOf(value.trim())));
            result.add(
                    new MetadataDirective(
                            REPAIR_EDITS_PREFIX,
                            value ->
                                    builder.expectedRepairEditCount =
                                            Integer.valueOf(value.trim())));
            return result;
        }

        private static void applyDirective(List<MetadataDirective> directives, String line) {
            for (MetadataDirective directive : directives) {
                if (directive.apply(line)) {
                    return;
                }
            }
        }

        private static String parseSearchMode(Path source, String value) {
            String searchValidationMode = value.trim();
            if (!"pass".equals(searchValidationMode) && !"decrease".equals(searchValidationMode)) {
                throw new AssertionError(
                        source + " has unknown repair search mode: " + searchValidationMode);
            }
            return searchValidationMode;
        }

        private static final class RepairMetadataBuilder {
            private String expectedDiagnosticKind = null;
            private RepairCase.ValidationMode validationMode = RepairCase.ValidationMode.MUST_PASS;
            private List<String> expectedPatchFragments = new ArrayList<>();
            private List<String> expectedRepairKinds = new ArrayList<>();
            private String expectedRepairRisk = null;
            private Boolean expectedRepairAutomatic = null;
            private Integer expectedRepairEditCount = null;
            private RepairCase.Runner runner = RepairCase.Runner.PATCH;
            private String searchValidationMode = null;
            private RiskLevel allowedRisk = null;
            private boolean expectedSearchAccepted = true;
            private Integer expectedSearchCandidateSize = null;
            private int maxCandidateSize = 2;
            private int maxSearchCandidates = 100;
            private boolean includeSketchEdits = false;
            private boolean expectSearchReport = false;
            private List<String> expectedSearchDiagnosticIds = new ArrayList<>();
            private Integer expectedGeneratedCandidateCount = null;
            private Integer expectedSearchedCandidateCount = null;
            private Integer expectedPrunedEmptyEditCount = null;
            private Integer expectedPrunedDuplicateEditCount = null;
            private Integer expectedPrunedOverlapCount = null;
            private Integer expectedPrunedBudgetCount = null;
            private List<RepairCase.ExpectedCount> expectedPrunedEmptyEditReasons =
                    new ArrayList<>();
            private List<String> expectedSearchEvents = new ArrayList<>();
            private List<String> expectedSearchReportFragments = new ArrayList<>();

            private RepairMetadata build(Path source) {
                if (expectedDiagnosticKind == null || expectedDiagnosticKind.isEmpty()) {
                    throw new AssertionError(source + " is missing // @repair-kind:");
                }
                if (expectedPatchFragments.isEmpty()
                        && validationMode != RepairCase.ValidationMode.SKETCH_ONLY) {
                    throw new AssertionError(source + " is missing // @repair-contains:");
                }
                if (searchValidationMode == null) {
                    searchValidationMode =
                            validationMode == RepairCase.ValidationMode.MUST_DECREASE
                                    ? "decrease"
                                    : "pass";
                }
                return new RepairMetadata(
                        expectedDiagnosticKind,
                        validationMode,
                        expectedPatchFragments,
                        expectedRepairKinds,
                        expectedRepairRisk,
                        expectedRepairAutomatic,
                        expectedRepairEditCount,
                        runner,
                        searchValidationMode,
                        allowedRisk,
                        expectedSearchAccepted,
                        expectedSearchCandidateSize,
                        maxCandidateSize,
                        maxSearchCandidates,
                        includeSketchEdits,
                        expectSearchReport,
                        expectedSearchDiagnosticIds,
                        expectedGeneratedCandidateCount,
                        expectedSearchedCandidateCount,
                        expectedPrunedEmptyEditCount,
                        expectedPrunedDuplicateEditCount,
                        expectedPrunedOverlapCount,
                        expectedPrunedBudgetCount,
                        expectedPrunedEmptyEditReasons,
                        expectedSearchEvents,
                        expectedSearchReportFragments);
            }
        }

        @FunctionalInterface
        private interface MetadataValueConsumer {
            void accept(String value);
        }

        private static final class MetadataDirective {
            private final String prefix;
            private final MetadataValueConsumer consumer;

            private MetadataDirective(String prefix, MetadataValueConsumer consumer) {
                this.prefix = prefix;
                this.consumer = consumer;
            }

            private boolean apply(String line) {
                if (!line.startsWith(prefix)) {
                    return false;
                }
                consumer.accept(line.substring(prefix.length()));
                return true;
            }
        }

        private static RepairCase.ExpectedCount parseExpectedCount(Path source, String value) {
            int separator = value.lastIndexOf('=');
            if (separator <= 0 || separator == value.length() - 1) {
                throw new AssertionError(
                        source + " expected count must have the form <key>=<count>: " + value);
            }
            String key = value.substring(0, separator).trim();
            int count = Integer.parseInt(value.substring(separator + 1).trim());
            return new RepairCase.ExpectedCount(key, count);
        }

        private static List<String> parseList(String value) {
            List<String> result = new ArrayList<>();
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        }

        private static RepairCase.ValidationMode parseValidationMode(String value) {
            String normalized = value.trim();
            if ("pass".equals(normalized)) {
                return RepairCase.ValidationMode.MUST_PASS;
            }
            if ("decrease".equals(normalized)) {
                return RepairCase.ValidationMode.MUST_DECREASE;
            }
            if ("sketch".equals(normalized)) {
                return RepairCase.ValidationMode.SKETCH_ONLY;
            }
            throw new AssertionError("unknown repair mode: " + value);
        }

        private static RepairCase.Runner parseRunner(String value) {
            String normalized = value.trim();
            if ("patch".equals(normalized)) {
                return RepairCase.Runner.PATCH;
            }
            if ("search".equals(normalized)) {
                return RepairCase.Runner.SEARCH;
            }
            if ("agent".equals(normalized)) {
                return RepairCase.Runner.AGENT;
            }
            throw new AssertionError("unknown repair runner: " + value);
        }
    }
}
