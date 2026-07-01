package checker_reconcile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import checker_reconcile.constraints.TraceModel;
import checker_reconcile.corpus.CorpusAttempt;
import checker_reconcile.corpus.CorpusReportJson;
import checker_reconcile.corpus.CorpusRunner;
import checker_reconcile.corpus.CorpusSummary;
import checker_reconcile.diagnosis.DiagnosisEngine;
import checker_reconcile.diagnosis.Mcs;
import checker_reconcile.diagnosis.Mus;
import checker_reconcile.diagnosis.RepairCandidate;
import checker_reconcile.diagnosis.RepairCandidateSet;
import checker_reconcile.diagnosis.SearchReportJson;
import checker_reconcile.diagnosis.SearchReportParser;
import checker_reconcile.diagnosis.ValidationBackedRepairSearch;
import checker_reconcile.experiments.CfTestDiagnosticMiner;
import checker_reconcile.experiments.CfTestDiagnosticMiner.Candidate;
import checker_reconcile.experiments.RepairMatrix;
import checker_reconcile.repair.AgentContextJson;
import checker_reconcile.repair.AgentContextParser;
import checker_reconcile.repair.AgentProposalParser;
import checker_reconcile.repair.NullCheckEditPlanner;
import checker_reconcile.repair.Patcher;
import checker_reconcile.repair.PlannedRepair;
import checker_reconcile.repair.RepairKind;
import checker_reconcile.repair.RepairPlanJson;
import checker_reconcile.repair.RepairPlanner;
import checker_reconcile.repair.RepairSketch;
import checker_reconcile.repair.RiskLevel;
import checker_reconcile.repair.SourceEdit;
import checker_reconcile.repair.SuggestedRepair;
import checker_reconcile.repair.Validation;
import checker_reconcile.repair.ValidationReportJson;
import checker_reconcile.repair.ValidationReportParser;
import checker_reconcile.trace.TraceEvent;
import checker_reconcile.trace.TraceParser;

/** Command-line entry point for the Java external reconciliation prototype. */
public final class Cli {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            usage();
            System.exit(2);
        }
        switch (args[0]) {
            case "diagnose":
                diagnose(args);
                break;
            case "repair":
                repair(args);
                break;
            case "plan":
                plan(args);
                break;
            case "apply-plan":
                applyPlan(args);
                break;
            case "lint-plan":
                lintPlan(args);
                break;
            case "search-repair":
                searchRepair(args);
                break;
            case "validate":
                validate(args);
                break;
            case "mine-cf-tests":
                mineCfTests(args);
                break;
            case "repair-matrix":
                repairMatrix(args);
                break;
            case "corpus-report":
                corpusReport(args);
                break;
            case "corpus-inspect":
                corpusInspect(args);
                break;
            case "agent-context":
                agentContext(args);
                break;
            case "agent-repair":
                agentRepair(args);
                break;
            case "agent-plan":
                agentPlan(args);
                break;
            default:
                usage();
                System.exit(2);
        }
    }

    private static void lintPlan(String[] args) throws Exception {
        Path source = null;
        Path plan = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--source")) {
                source = Paths.get(args[++i]);
            } else if (args[i].equals("--plan")) {
                plan = Paths.get(args[++i]);
            }
        }
        if (source == null || plan == null) {
            usage();
            System.exit(2);
        }
        List<PlannedRepair> repairs = plannedRepairs(source, plan);
        System.out.println("plan: " + plan);
        System.out.println("total: " + repairs.size());
        printPlanCounts("origin", originCounts(repairs));
        printPlanCounts("kind", kindCounts(repairs));
        printPlanCounts("risk", riskCounts(repairs));
        printPlanCounts("automatic", automaticCounts(repairs));
        System.out.println("edit-count: " + totalEditCount(repairs));
        System.out.println("requires-validation: " + requiresValidationCount(repairs));
        System.out.println("agent-automatic-edits: " + agentAutomaticEditCount(repairs));
    }

    private static void diagnose(String[] args) throws Exception {
        if (args.length < 2) {
            usage();
            System.exit(2);
        }
        Path trace = Paths.get(args[1]);
        Path source = null;
        if (args.length >= 4 && args[2].equals("--source")) {
            source = Paths.get(args[3]);
        }
        TraceModel model = load(trace);
        Mus mus = new Mus();
        Mcs mcs = new Mcs();
        DiagnosisEngine diagnosisEngine = new DiagnosisEngine();
        for (String diagnosticId : model.diagnostics.keySet()) {
            TraceModel.DiagnosticSlice slice = model.slice(diagnosticId);
            System.out.println(
                    "diagnostic "
                            + diagnosticId
                            + ": "
                            + slice.diagnostic().stringField("error_kind"));
            System.out.println("  obligation: " + slice.obligation().id);
            System.out.println("  mus: " + mus.compute(slice));
            System.out.println("  mcs: " + mcs.compute(slice));
            if (source != null) {
                printCandidates(diagnosisEngine.diagnose(source, model, slice));
                for (SuggestedRepair repair : new RepairPlanner().plan(source, slice)) {
                    printRepair(repair);
                }
            } else {
                System.out.println("  repair plans: pass --source SOURCE.java to resolve edits");
            }
        }
    }

    private static void printCandidates(List<RepairCandidateSet> candidateSets) {
        System.out.println("  candidates:");
        if (candidateSets.isEmpty()) {
            System.out.println("    none");
            return;
        }
        int index = 1;
        for (RepairCandidateSet candidateSet : candidateSets) {
            System.out.println(
                    "    "
                            + index
                            + ". cost="
                            + candidateSet.cost().value()
                            + " size="
                            + candidateSet.candidates().size()
                            + " diagnostics="
                            + candidateSet.diagnosticIds());
            for (RepairCandidate candidate : candidateSet.candidates()) {
                System.out.println(
                        "       "
                                + candidate.kind()
                                + " risk="
                                + candidate.risk()
                                + " automatic="
                                + candidate.automatic()
                                + " assumptions="
                                + candidate.assumptionIds()
                                + " cost="
                                + candidate.cost().value()
                                + " - "
                                + candidate.message());
            }
            index++;
        }
    }

    private static void printRepair(SuggestedRepair repair) {
        System.out.println(
                "  repair: "
                        + repair.kind()
                        + " risk="
                        + repair.risk()
                        + " automatic="
                        + repair.automatic()
                        + " evidence="
                        + repair.evidenceIds()
                        + " - "
                        + repair.message());
        for (SourceEdit edit : repair.edits()) {
            System.out.println(
                    "    edit: "
                            + edit.file()
                            + ":"
                            + edit.startOffset()
                            + "-"
                            + edit.endOffset()
                            + " -> "
                            + edit.replacement());
        }
    }

    private static void repair(String[] args) throws Exception {
        Path source = null;
        Path trace = null;
        Path out = null;
        boolean validate = false;
        String validationMode = "pass";
        String javac = "javac";
        Path checker = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--source")) {
                source = Paths.get(args[++i]);
            } else if (args[i].equals("--trace")) {
                trace = Paths.get(args[++i]);
            } else if (args[i].equals("--out")) {
                out = Paths.get(args[++i]);
            } else if (args[i].equals("--validate")) {
                validate = true;
            } else if (args[i].equals("--validation-mode")) {
                validationMode = args[++i];
            } else if (args[i].equals("--javac")) {
                javac = args[++i];
            } else if (args[i].equals("--checker")) {
                checker = Paths.get(args[++i]);
            }
        }
        if (source == null || trace == null || out == null) {
            usage();
            System.exit(2);
        }
        TraceModel model = load(trace);
        if (model.diagnostics.isEmpty()) {
            new Patcher().writePatched(source, out, Collections.emptyList());
        } else {
            TraceModel.DiagnosticSlice slice =
                    model.slice(model.diagnostics.keySet().iterator().next());
            new Patcher().writePatched(source, out, slice);
        }
        System.out.println("wrote " + out + " (no body-changing automatic edits in V0)");
        if (validate) {
            validatePatch(javac, checker, source, out, validationMode);
        }
    }

    private static void plan(String[] args) throws Exception {
        Path source = null;
        Path trace = null;
        boolean includeSketchEdits = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--source")) {
                source = Paths.get(args[++i]);
            } else if (args[i].equals("--trace")) {
                trace = Paths.get(args[++i]);
            } else if (args[i].equals("--include-sketch-edits")) {
                includeSketchEdits = true;
            }
        }
        if (source == null || trace == null) {
            usage();
            System.exit(2);
        }
        TraceModel model = load(trace);
        RepairPlanner planner = new RepairPlanner();
        RepairPlanJson json = new RepairPlanJson();
        for (String diagnosticId : model.diagnostics.keySet()) {
            TraceModel.DiagnosticSlice slice = model.slice(diagnosticId);
            List<SuggestedRepair> repairs = planner.plan(source, slice);
            if (includeSketchEdits) {
                repairs = new NullCheckEditPlanner().addNullCheckEdits(source, repairs);
            }
            for (SuggestedRepair repair : repairs) {
                System.out.println(json.toJson(diagnosticId, repair));
            }
        }
    }

    private static void applyPlan(String[] args) throws Exception {
        Path source = null;
        Path plan = null;
        Path out = null;
        boolean validate = false;
        boolean dryRun = false;
        String validationMode = "pass";
        String javac = "javac";
        Path checker = null;
        Path validationReport = null;
        String diagnosticFilter = null;
        RepairKind kindFilter = null;
        Set<RiskLevel> riskFilter = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--source")) {
                source = Paths.get(args[++i]);
            } else if (args[i].equals("--plan")) {
                plan = Paths.get(args[++i]);
            } else if (args[i].equals("--out")) {
                out = Paths.get(args[++i]);
            } else if (args[i].equals("--validate")) {
                validate = true;
            } else if (args[i].equals("--validation-mode")) {
                validationMode = args[++i];
            } else if (args[i].equals("--dry-run")) {
                dryRun = true;
            } else if (args[i].equals("--javac")) {
                javac = args[++i];
            } else if (args[i].equals("--checker")) {
                checker = Paths.get(args[++i]);
            } else if (args[i].equals("--validation-report")) {
                validationReport = Paths.get(args[++i]);
            } else if (args[i].equals("--diagnostic")) {
                diagnosticFilter = args[++i];
            } else if (args[i].equals("--kind")) {
                kindFilter = RepairKind.valueOf(args[++i]);
            } else if (args[i].equals("--allow-risk")) {
                riskFilter = parseRisks(args[++i], riskFilter);
            }
        }
        if (source == null || plan == null || out == null) {
            usage();
            System.exit(2);
        }
        if (validationReport != null && !validate) {
            throw new IllegalArgumentException("--validation-report requires --validate");
        }
        List<PlannedRepair> selectedRepairs =
                selectedRepairs(source, plan, diagnosticFilter, kindFilter, riskFilter);
        if (dryRun) {
            RepairPlanJson json = new RepairPlanJson();
            for (PlannedRepair plannedRepair : selectedRepairs) {
                SuggestedRepair repair = plannedRepair.repair();
                if (repair.automatic() && !repair.edits().isEmpty()) {
                    System.out.println(json.toJson(plannedRepair.diagnosticId(), repair));
                }
            }
            return;
        }
        rejectUnsafeAgentRepairsWithoutValidation(selectedRepairs, validate);
        new Patcher().writePlanned(source, out, repairs(selectedRepairs));
        System.out.println("wrote " + out + " from automatic plan edits");
        if (validate) {
            ValidationOutcome outcome =
                    validatePatchDetailed(javac, checker, source, out, validationMode);
            if (validationReport != null) {
                Files.write(
                        validationReport,
                        List.of(
                                new ValidationReportJson()
                                        .toJson(
                                                source,
                                                out,
                                                validationMode,
                                                outcome.accepted,
                                                outcome.before,
                                                outcome.after,
                                                selectedRepairs)),
                        StandardCharsets.UTF_8);
            }
            if (!outcome.accepted) {
                System.exit(outcome.exitCode());
            }
        }
    }

    private static void searchRepair(String[] args) throws Exception {
        Path source = null;
        Path trace = null;
        Path out = null;
        String validationMode = "pass";
        String javac = "javac";
        Path checker = null;
        Set<RiskLevel> riskFilter = null;
        String diagnosticFilter = null;
        boolean explainSearch = false;
        int maxCandidateSize = ValidationBackedRepairSearch.DEFAULT_MAX_CANDIDATE_SIZE;
        int maxSearchCandidates = ValidationBackedRepairSearch.DEFAULT_MAX_SEARCH_CANDIDATES;
        Path searchReport = null;
        boolean includeSketchEdits = false;
        int searchRounds = 1;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--source")) {
                source = Paths.get(args[++i]);
            } else if (args[i].equals("--trace")) {
                trace = Paths.get(args[++i]);
            } else if (args[i].equals("--out")) {
                out = Paths.get(args[++i]);
            } else if (args[i].equals("--validation-mode")) {
                validationMode = args[++i];
            } else if (args[i].equals("--javac")) {
                javac = args[++i];
            } else if (args[i].equals("--checker")) {
                checker = Paths.get(args[++i]);
            } else if (args[i].equals("--allow-risk")) {
                riskFilter = parseRisks(args[++i], riskFilter);
            } else if (args[i].equals("--diagnostic")) {
                diagnosticFilter = args[++i];
            } else if (args[i].equals("--explain-search")) {
                explainSearch = true;
            } else if (args[i].equals("--max-candidate-size")) {
                maxCandidateSize = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--max-search-candidates")) {
                maxSearchCandidates = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--search-report")) {
                searchReport = Paths.get(args[++i]);
            } else if (args[i].equals("--include-sketch-edits")) {
                includeSketchEdits = true;
            } else if (args[i].equals("--search-rounds")) {
                searchRounds = Integer.parseInt(args[++i]);
            }
        }
        if (source == null || trace == null || out == null) {
            usage();
            System.exit(2);
        }
        TraceModel model = load(trace);
        List<String> searchReportLines = new ArrayList<>();
        ValidationBackedRepairSearch.Listener listener =
                searchListener(explainSearch, searchReportLines);
        ValidationBackedRepairSearch.Result result =
                new ValidationBackedRepairSearch()
                        .search(
                                source,
                                model,
                                out,
                                javac,
                                checker,
                                validationMode,
                                riskFilter,
                                diagnosticFilter == null
                                        ? null
                                        : new LinkedHashSet<>(List.of(diagnosticFilter)),
                                listener,
                                maxCandidateSize,
                                maxSearchCandidates,
                                includeSketchEdits,
                                searchRounds);
        if (searchReport != null) {
            SearchReportJson json = new SearchReportJson();
            searchReportLines.add(json.summary(result));
            Files.write(searchReport, searchReportLines, StandardCharsets.UTF_8);
        }
        if (result.accepted()) {
            System.out.println(
                    "search accepted "
                            + out
                            + " (diagnostics "
                            + result.before().diagnosticCount()
                            + " -> "
                            + result.after().diagnosticCount()
                            + ", candidate cost="
                            + result.candidateSet().cost().value()
                            + ")");
        } else {
            System.err.println(
                    "search found no accepted patch (diagnostics "
                            + result.before().diagnosticCount()
                            + ")");
            System.exit(1);
        }
    }

    private static ValidationBackedRepairSearch.Listener searchListener(
            boolean explainSearch, List<String> reportLines) {
        if (!explainSearch && reportLines == null) {
            return ValidationBackedRepairSearch.Listener.NOOP;
        }
        ValidationBackedRepairSearch.Listener explain =
                explainSearch
                        ? explainSearchListener()
                        : ValidationBackedRepairSearch.Listener.NOOP;
        ValidationBackedRepairSearch.Listener report =
                reportLines == null
                        ? ValidationBackedRepairSearch.Listener.NOOP
                        : searchReportListener(reportLines);
        return new ValidationBackedRepairSearch.Listener() {
            @Override
            public void skipped(int index, RepairCandidateSet candidateSet, String reason) {
                explain.skipped(index, candidateSet, reason);
                report.skipped(index, candidateSet, reason);
            }

            @Override
            public void validated(
                    int index,
                    RepairCandidateSet candidateSet,
                    Validation.Result after,
                    boolean accepted) {
                explain.validated(index, candidateSet, after, accepted);
                report.validated(index, candidateSet, after, accepted);
            }

            @Override
            public void invalid(int index, RepairCandidateSet candidateSet, String reason) {
                explain.invalid(index, candidateSet, reason);
                report.invalid(index, candidateSet, reason);
            }

            @Override
            public void pruned(int index, RepairCandidateSet candidateSet, String reason) {
                explain.pruned(index, candidateSet, reason);
                report.pruned(index, candidateSet, reason);
            }
        };
    }

    private static ValidationBackedRepairSearch.Listener searchReportListener(List<String> lines) {
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
                    Validation.Result after,
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

    private static ValidationBackedRepairSearch.Listener explainSearchListener() {
        return new ValidationBackedRepairSearch.Listener() {
            @Override
            public void skipped(int index, RepairCandidateSet candidateSet, String reason) {
                System.out.println(
                        "candidate "
                                + index
                                + " cost="
                                + candidateSet.cost().value()
                                + " diagnostics="
                                + candidateSet.diagnosticIds()
                                + " repairs="
                                + repairSummary(candidateSet)
                                + " validation=skipped reason=\""
                                + reason
                                + "\"");
            }

            @Override
            public void validated(
                    int index,
                    RepairCandidateSet candidateSet,
                    Validation.Result after,
                    boolean accepted) {
                System.out.println(
                        "candidate "
                                + index
                                + " cost="
                                + candidateSet.cost().value()
                                + " diagnostics="
                                + candidateSet.diagnosticIds()
                                + " repairs="
                                + repairSummary(candidateSet)
                                + " validation="
                                + (accepted ? "accepted" : "rejected")
                                + " diagnostics="
                                + after.diagnosticCount()
                                + " exit="
                                + after.exitCode());
            }

            @Override
            public void invalid(int index, RepairCandidateSet candidateSet, String reason) {
                System.out.println(
                        "candidate "
                                + index
                                + " cost="
                                + candidateSet.cost().value()
                                + " diagnostics="
                                + candidateSet.diagnosticIds()
                                + " repairs="
                                + repairSummary(candidateSet)
                                + " validation=invalid reason=\""
                                + reason
                                + "\"");
            }
        };
    }

    private static String repairSummary(RepairCandidateSet candidateSet) {
        List<String> repairs = new ArrayList<>();
        for (RepairCandidate candidate : candidateSet.candidates()) {
            repairs.add(candidate.kind() + "/" + candidate.risk());
        }
        return repairs.toString();
    }

    private static void validatePatch(
            String javac, Path checker, Path source, Path out, String validationMode)
            throws Exception {
        ValidationOutcome outcome =
                validatePatchDetailed(javac, checker, source, out, validationMode);
        if (!outcome.accepted) {
            System.exit(outcome.exitCode());
        }
    }

    private static ValidationOutcome validatePatchDetailed(
            String javac, Path checker, Path source, Path out, String validationMode)
            throws Exception {
        Validation validation = new Validation();
        if (validationMode.equals("pass")) {
            Validation.Result after = validation.validateDetailed(javac, checker, out);
            if (after.exitCode() == 0) {
                System.out.println("validation accepted " + out);
                return new ValidationOutcome(true, null, after);
            } else {
                System.err.println(
                        "validation rejected " + out + " with exit code " + after.exitCode());
                return new ValidationOutcome(false, null, after);
            }
        } else if (validationMode.equals("decrease")) {
            Validation.Result before = validation.validateDetailed(javac, checker, source);
            Validation.Result after = validation.validateDetailed(javac, checker, out);
            if (after.diagnosticCount() < before.diagnosticCount()) {
                System.out.println(
                        "validation accepted "
                                + out
                                + " (diagnostics "
                                + before.diagnosticCount()
                                + " -> "
                                + after.diagnosticCount()
                                + ")");
                return new ValidationOutcome(true, before, after);
            } else {
                System.err.println(
                        "validation rejected "
                                + out
                                + " (diagnostics "
                                + before.diagnosticCount()
                                + " -> "
                                + after.diagnosticCount()
                                + ")");
                return new ValidationOutcome(false, before, after);
            }
        } else {
            throw new IllegalArgumentException("unknown validation mode: " + validationMode);
        }
    }

    private static final class ValidationOutcome {
        private final boolean accepted;
        private final Validation.Result before;
        private final Validation.Result after;

        ValidationOutcome(boolean accepted, Validation.Result before, Validation.Result after) {
            this.accepted = accepted;
            this.before = before;
            this.after = after;
        }

        int exitCode() {
            if (accepted) {
                return 0;
            }
            if (after == null || after.exitCode() == 0) {
                return 1;
            }
            return after.exitCode();
        }
    }

    private static List<PlannedRepair> selectedRepairs(
            Path source,
            Path plan,
            String diagnosticFilter,
            RepairKind kindFilter,
            Set<RiskLevel> riskFilter)
            throws Exception {
        List<PlannedRepair> repairs = new ArrayList<>();
        for (PlannedRepair plannedRepair : plannedRepairs(source, plan)) {
            if (diagnosticFilter != null
                    && !diagnosticFilter.equals(plannedRepair.diagnosticId())) {
                continue;
            }
            SuggestedRepair repair = plannedRepair.repair();
            if (kindFilter != null && kindFilter != repair.kind()) {
                continue;
            }
            if (riskFilter != null && !riskFilter.contains(repair.risk())) {
                continue;
            }
            repairs.add(plannedRepair);
        }
        return repairs;
    }

    private static void rejectUnsafeAgentRepairsWithoutValidation(
            List<PlannedRepair> repairs, boolean validate) {
        if (validate) {
            return;
        }
        for (PlannedRepair plannedRepair : repairs) {
            SuggestedRepair repair = plannedRepair.repair();
            if (!repair.automatic() || repair.edits().isEmpty()) {
                continue;
            }
            if (plannedRepair.requiresValidation() || plannedRepair.origin().equals("agent")) {
                throw new IllegalArgumentException(
                        "plan entry "
                                + plannedRepair.diagnosticId()
                                + " from origin "
                                + plannedRepair.origin()
                                + " requires --validate before applying automatic edits");
            }
        }
    }

    private static List<SuggestedRepair> repairs(List<PlannedRepair> plannedRepairs) {
        List<SuggestedRepair> repairs = new ArrayList<>();
        for (PlannedRepair plannedRepair : plannedRepairs) {
            repairs.add(plannedRepair.repair());
        }
        return repairs;
    }

    private static Map<String, Integer> originCounts(List<PlannedRepair> repairs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlannedRepair repair : repairs) {
            increment(counts, repair.origin());
        }
        return counts;
    }

    private static Map<String, Integer> kindCounts(List<PlannedRepair> repairs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlannedRepair plannedRepair : repairs) {
            increment(counts, plannedRepair.repair().kind().name());
        }
        return counts;
    }

    private static Map<String, Integer> riskCounts(List<PlannedRepair> repairs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlannedRepair plannedRepair : repairs) {
            increment(counts, plannedRepair.repair().risk().name());
        }
        return counts;
    }

    private static Map<String, Integer> automaticCounts(List<PlannedRepair> repairs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlannedRepair plannedRepair : repairs) {
            increment(counts, Boolean.toString(plannedRepair.repair().automatic()));
        }
        return counts;
    }

    private static int totalEditCount(List<PlannedRepair> repairs) {
        int count = 0;
        for (PlannedRepair plannedRepair : repairs) {
            count += plannedRepair.repair().edits().size();
        }
        return count;
    }

    private static int requiresValidationCount(List<PlannedRepair> repairs) {
        int count = 0;
        for (PlannedRepair plannedRepair : repairs) {
            if (plannedRepair.requiresValidation()) {
                count++;
            }
        }
        return count;
    }

    private static int agentAutomaticEditCount(List<PlannedRepair> repairs) {
        int count = 0;
        for (PlannedRepair plannedRepair : repairs) {
            SuggestedRepair repair = plannedRepair.repair();
            if (plannedRepair.origin().equals("agent")
                    && repair.automatic()
                    && !repair.edits().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static void printPlanCounts(String label, Map<String, Integer> counts) {
        System.out.println(label + ":");
        if (counts.isEmpty()) {
            System.out.println("  <none>: 0");
            return;
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
    }

    private static void increment(Map<String, Integer> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }

    private static List<PlannedRepair> plannedRepairs(Path source, Path plan) throws Exception {
        List<PlannedRepair> repairs = new ArrayList<>();
        for (TraceEvent event : new TraceParser().parse(plan)) {
            requireSchemaVersion(event);
            repairs.add(
                    new PlannedRepair(
                            requireString(event, "diagnostic_id"),
                            plannedRepair(source, event),
                            optionalOrigin(event),
                            optionalConfidence(event),
                            optionalRequiresValidation(event)));
        }
        return repairs;
    }

    private static SuggestedRepair plannedRepair(Path source, TraceEvent event) {
        requireSchemaVersion(event);
        String kind = requireString(event, "kind");
        String risk = requireString(event, "risk");
        boolean automatic = requireBoolean(event, "automatic");
        requireList(event, "evidence_ids");
        requireList(event, "edits");
        return new SuggestedRepair(
                repairKind(kind),
                plannedEdits(source, event),
                riskLevel(risk),
                automatic,
                stringList(event.listField("evidence_ids")),
                requireString(event, "message"),
                plannedSketches(event));
    }

    private static void requireSchemaVersion(TraceEvent event) {
        Object value = event.fields.get("schema_version");
        if (!(value instanceof Number) || ((Number) value).intValue() != 1) {
            throw new IllegalArgumentException("plan entry requires schema_version 1");
        }
    }

    private static String optionalOrigin(TraceEvent event) {
        Object value = event.fields.get("origin");
        if (value == null) {
            return "deterministic";
        }
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalArgumentException("plan entry origin must be a non-empty string");
        }
        return (String) value;
    }

    private static Double optionalConfidence(TraceEvent event) {
        Object value = event.fields.get("confidence");
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("plan entry confidence must be numeric");
        }
        double confidence = ((Number) value).doubleValue();
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("plan entry confidence must be between 0 and 1");
        }
        return confidence;
    }

    private static boolean optionalRequiresValidation(TraceEvent event) {
        Object value = event.fields.get("requires_validation");
        if (value == null) {
            return false;
        }
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("plan entry requires_validation must be boolean");
        }
        return ((Boolean) value).booleanValue();
    }

    private static String requireString(TraceEvent event, String field) {
        String value = event.stringField(field);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("plan entry missing " + field);
        }
        return value;
    }

    private static boolean requireBoolean(TraceEvent event, String field) {
        Object value = event.fields.get(field);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("plan entry missing boolean " + field);
        }
        return ((Boolean) value).booleanValue();
    }

    private static List<Object> requireList(TraceEvent event, String field) {
        Object value = event.fields.get(field);
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException("plan entry missing list " + field);
        }
        return event.listField(field);
    }

    @SuppressWarnings("unchecked")
    private static List<RepairSketch> plannedSketches(TraceEvent event) {
        Object sketchesValue = event.fields.get("sketches");
        if (sketchesValue == null) {
            return Collections.emptyList();
        }
        if (!(sketchesValue instanceof List<?>)) {
            throw new IllegalArgumentException("plan entry sketches must be a list");
        }
        List<RepairSketch> sketches = new ArrayList<>();
        for (Object sketchValue : event.listField("sketches")) {
            if (!(sketchValue instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("plan sketch must be an object");
            }
            Map<String, Object> sketch = (Map<String, Object>) sketchValue;
            String sourceTargetKind = "";
            String expression = "";
            Integer startOffset = null;
            Integer endOffset = null;
            Map<String, Object> sourceTargetAttributes = new LinkedHashMap<>();
            Object sourceTargetValue = sketch.get("source_target");
            if (sourceTargetValue instanceof Map<?, ?>) {
                Map<String, Object> sourceTarget = (Map<String, Object>) sourceTargetValue;
                sourceTargetKind = stringValue(sourceTarget.get("kind"));
                expression = stringValue(sourceTarget.get("expression"));
                sourceTargetAttributes.putAll(sourceTarget);
                sourceTargetAttributes.remove("kind");
                sourceTargetAttributes.remove("expression");
                sourceTargetAttributes.remove("expression_range");
                Object rangeValue = sourceTarget.get("expression_range");
                if (rangeValue instanceof Map<?, ?>) {
                    Map<String, Object> range = (Map<String, Object>) rangeValue;
                    startOffset = intField(range, "start_offset");
                    endOffset = intField(range, "end_offset");
                    if (startOffset < 0 || endOffset < startOffset) {
                        throw new IllegalArgumentException("plan sketch has invalid range");
                    }
                }
            } else if (sourceTargetValue != null) {
                throw new IllegalArgumentException("plan sketch source_target must be an object");
            }
            sketches.add(
                    new RepairSketch(
                            stringValue(sketch.get("kind")),
                            stringValue(sketch.get("target_id")),
                            booleanValue(sketch.get("automatic")),
                            stringValue(sketch.get("message")),
                            sourceTargetKind,
                            expression,
                            startOffset,
                            endOffset,
                            sourceTargetAttributes,
                            stringValue(sketch.get("materialization_failure"))));
        }
        return sketches;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static RepairKind repairKind(String value) {
        try {
            return RepairKind.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("plan entry invalid kind: " + value, e);
        }
    }

    private static RiskLevel riskLevel(String value) {
        try {
            return RiskLevel.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("plan entry invalid risk: " + value, e);
        }
    }

    private static Set<RiskLevel> parseRisks(String value, Set<RiskLevel> existing) {
        Set<RiskLevel> risks =
                existing == null ? new LinkedHashSet<>() : new LinkedHashSet<>(existing);
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                risks.add(RiskLevel.valueOf(trimmed));
            }
        }
        if (risks.isEmpty()) {
            throw new IllegalArgumentException("--allow-risk requires at least one risk");
        }
        return risks;
    }

    @SuppressWarnings("unchecked")
    private static List<SourceEdit> plannedEdits(Path source, TraceEvent event) {
        List<SourceEdit> edits = new ArrayList<>();
        for (Object editValue : event.listField("edits")) {
            if (!(editValue instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("plan edit must be an object");
            }
            Map<String, Object> edit = (Map<String, Object>) editValue;
            Object replacement = edit.get("replacement");
            if (!(replacement instanceof String)) {
                throw new IllegalArgumentException("plan edit missing replacement");
            }
            edits.add(
                    new SourceEdit(
                            source,
                            intField(edit, "start_offset"),
                            intField(edit, "end_offset"),
                            (String) replacement));
        }
        return edits;
    }

    private static List<String> stringList(List<Object> values) {
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            result.add(value.toString());
        }
        return result;
    }

    private static int intField(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("plan edit missing numeric " + name);
        }
        return ((Number) value).intValue();
    }

    private static void validate(String[] args) throws Exception {
        String javac = "javac";
        Path checker = null;
        Path source = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--javac")) {
                javac = args[++i];
            } else if (args[i].equals("--checker")) {
                checker = Paths.get(args[++i]);
            } else {
                source = Paths.get(args[i]);
            }
        }
        if (source == null) {
            usage();
            System.exit(2);
        }
        System.exit(new Validation().validate(javac, checker, source));
    }

    private static void mineCfTests(String[] args) throws Exception {
        Path root = null;
        boolean examples = false;
        boolean repairCandidates = false;
        int limit = 20;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--root")) {
                root = Paths.get(args[++i]);
            } else if (args[i].equals("--examples")) {
                examples = true;
            } else if (args[i].equals("--repair-candidates")) {
                repairCandidates = true;
            } else if (args[i].equals("--limit")) {
                limit = Integer.parseInt(args[++i]);
            }
        }
        if (root == null) {
            usage();
            System.exit(2);
        }
        CfTestDiagnosticMiner miner = new CfTestDiagnosticMiner();
        List<Candidate> candidates = miner.mine(root);
        System.out.println("root: " + root);
        System.out.println("total: " + candidates.size());
        for (java.util.Map.Entry<String, Integer> entry :
                miner.countsByKind(candidates).entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        List<Candidate> likelyRepairs = miner.likelyRepairCandidates(candidates);
        List<Candidate> likelyLocalRepairs = miner.likelyLocalAnnotationRepairs(candidates);
        System.out.println("likely-repair-candidates: " + likelyRepairs.size());
        System.out.println("likely-local-annotation-repairs: " + likelyLocalRepairs.size());
        if (examples) {
            System.out.println("examples:");
            for (Candidate candidate : miner.firstPerKind(candidates)) {
                System.out.println("  " + candidate);
            }
        }
        if (repairCandidates) {
            System.out.println("repair candidates:");
            for (int i = 0; i < likelyRepairs.size() && i < limit; i++) {
                Candidate candidate = likelyRepairs.get(i);
                System.out.println(
                        "  "
                                + candidate
                                + " "
                                + candidate.likelyRepairKinds()
                                + " :: "
                                + candidate.codeLine());
            }
        }
    }

    private static void repairMatrix(String[] args) throws Exception {
        Path tests = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--tests")) {
                tests = Paths.get(args[++i]);
            }
        }
        if (tests == null) {
            usage();
            System.exit(2);
        }
        System.out.print(new RepairMatrix().summarize(tests).render());
    }

    private static void corpusReport(String[] args) throws Exception {
        Path root = null;
        Path out = null;
        Path summary = null;
        Path workDir = null;
        String javac = "javac";
        Path checker = null;
        String validationMode = "decrease";
        Set<RiskLevel> allowedRisks = null;
        int maxCandidateSize = 3;
        int maxSearchCandidates = ValidationBackedRepairSearch.DEFAULT_MAX_SEARCH_CANDIDATES;
        int limit = 100;
        boolean includeSketchEdits = false;
        int searchRounds = 1;
        boolean verboseDiagnostics = false;
        boolean progress = false;
        boolean defaultNullnessOptions = true;
        List<String> javacOptions = new ArrayList<>();
        List<Path> classpathExtra = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--root")) {
                root = Paths.get(args[++i]);
            } else if (args[i].equals("--out")) {
                out = Paths.get(args[++i]);
            } else if (args[i].equals("--summary")) {
                summary = Paths.get(args[++i]);
            } else if (args[i].equals("--work-dir")) {
                workDir = Paths.get(args[++i]);
            } else if (args[i].equals("--javac")) {
                javac = args[++i];
            } else if (args[i].equals("--checker")) {
                checker = Paths.get(args[++i]);
            } else if (args[i].equals("--validation-mode")) {
                validationMode = args[++i];
            } else if (args[i].equals("--allow-risk")) {
                allowedRisks = parseRisks(args[++i], allowedRisks);
            } else if (args[i].equals("--max-candidate-size")) {
                maxCandidateSize = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--max-search-candidates")) {
                maxSearchCandidates = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--limit")) {
                limit = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--include-sketch-edits")) {
                includeSketchEdits = true;
            } else if (args[i].equals("--search-rounds")) {
                searchRounds = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--verbose-diagnostics")) {
                verboseDiagnostics = true;
            } else if (args[i].equals("--progress")) {
                progress = true;
            } else if (args[i].equals("--no-default-nullness-options")) {
                defaultNullnessOptions = false;
            } else if (args[i].equals("--javac-option")) {
                javacOptions.add(args[++i]);
            } else if (args[i].equals("--classpath-extra")) {
                classpathExtra.add(Paths.get(args[++i]));
            }
        }
        if (root == null || out == null || summary == null) {
            usage();
            System.exit(2);
        }
        if (workDir == null) {
            Path parent = out.getParent() == null ? Paths.get(".") : out.getParent();
            workDir = parent.resolve("work");
        }
        if (allowedRisks == null) {
            allowedRisks = new LinkedHashSet<>();
            allowedRisks.add(RiskLevel.LOCAL_ONLY);
            allowedRisks.add(RiskLevel.API_CHANGE);
        }
        List<String> allJavacOptions = new ArrayList<>();
        if (defaultNullnessOptions) {
            allJavacOptions.addAll(CorpusRunner.defaultNullnessOptions());
        }
        allJavacOptions.addAll(javacOptions);
        CorpusRunner.Options options =
                new CorpusRunner.Options(
                        root,
                        workDir,
                        javac,
                        checker,
                        validationMode,
                        allowedRisks,
                        maxCandidateSize,
                        maxSearchCandidates,
                        limit,
                        includeSketchEdits,
                        searchRounds,
                        verboseDiagnostics,
                        progress,
                        allJavacOptions,
                        classpathExtra);
        List<CorpusAttempt> attempts = new CorpusRunner().run(options);
        CorpusSummary corpusSummary = new CorpusSummary(attempts);
        CorpusReportJson json = new CorpusReportJson();
        List<String> reportLines = new ArrayList<>();
        for (CorpusAttempt attempt : attempts) {
            reportLines.add(json.attempt(attempt));
        }
        reportLines.add(json.summary(corpusSummary));
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        if (summary.getParent() != null) {
            Files.createDirectories(summary.getParent());
        }
        Files.write(out, reportLines, StandardCharsets.UTF_8);
        Files.write(summary, List.of(corpusSummary.render()), StandardCharsets.UTF_8);
        System.out.print(corpusSummary.render());
        System.out.println("report: " + out);
        System.out.println("summary: " + summary);
    }

    private static void corpusInspect(String[] args) throws Exception {
        Path report = null;
        int limit = 5;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--report")) {
                report = Paths.get(args[++i]);
            } else if (args[i].equals("--limit")) {
                limit = Integer.parseInt(args[++i]);
            }
        }
        if (report == null) {
            usage();
            System.exit(2);
        }
        List<TraceEvent> events = new CorpusReportJson().parse(report);
        List<TraceEvent> attempts = new ArrayList<>();
        for (TraceEvent event : events) {
            if ("corpus_attempt".equals(event.stringField("event"))) {
                attempts.add(event);
            }
        }
        System.out.println("report: " + report);
        System.out.println("attempts: " + attempts.size());
        printCorpusInspectBucket(
                "accepted-not-full-pass", attempts, limit, Cli::isAcceptedNotFullPass);
        printCorpusInspectBucket("agent-refactor-target", attempts, limit, Cli::isAgentTarget);
        printCorpusInspectBucket("validation-rejected", attempts, limit, Cli::isValidationRejected);
        printCorpusInspectBucket("unsupported-or-no-edit", attempts, limit, Cli::isUnsupported);
        printCorpusInspectBucket("full-pass", attempts, limit, Cli::isFullPass);
    }

    private static void printCorpusInspectBucket(
            String title,
            List<TraceEvent> attempts,
            int limit,
            java.util.function.Predicate<TraceEvent> predicate) {
        System.out.println(title + ":");
        int count = 0;
        int printed = 0;
        for (TraceEvent attempt : attempts) {
            if (!predicate.test(attempt)) {
                continue;
            }
            count++;
            if (printed < limit) {
                printCorpusInspectAttempt(attempt);
                printed++;
            }
        }
        if (count == 0) {
            System.out.println("  <none>");
        } else if (count > printed) {
            System.out.println("  ... " + (count - printed) + " more");
        }
        System.out.println("  total: " + count);
    }

    private static void printCorpusInspectAttempt(TraceEvent attempt) {
        String diagnosticId = attempt.stringField("diagnostic_id");
        String source = attempt.stringField("source");
        String key = diagnosticId.isEmpty() ? source : source + "#" + diagnosticId;
        System.out.println("  - " + key);
        printCorpusInspectField(attempt, "kind", "diagnostic_kind");
        printCorpusInspectField(attempt, "repair", "accepted_repair_kind");
        printCorpusInspectField(attempt, "risk", "accepted_risk");
        printCorpusInspectField(attempt, "failure", "failure_reason");
        printCorpusInspectField(attempt, "planner", "planner_reason");
        printCorpusInspectField(attempt, "agent-context", "agent_refactor_context");
        printCorpusInspectField(attempt, "original", "original_source");
        printCorpusInspectField(attempt, "trace", "trace_path");
        printCorpusInspectField(attempt, "work-source", "work_source");
        printCorpusInspectField(attempt, "patched", "patched_source");
        System.out.println(
                "    diagnostics: "
                        + attempt.stringField("before_diagnostic_count")
                        + " -> "
                        + attempt.stringField("after_diagnostic_count"));
    }

    private static void printCorpusInspectField(
            TraceEvent attempt, String label, String fieldName) {
        String value = attempt.stringField(fieldName);
        if (!value.isEmpty()) {
            System.out.println("    " + label + ": " + value);
        }
    }

    private static boolean isAcceptedNotFullPass(TraceEvent attempt) {
        return boolField(attempt, "accepted") && !boolField(attempt, "full_pass");
    }

    private static boolean isAgentTarget(TraceEvent attempt) {
        return boolField(attempt, "agent_refactor_target");
    }

    private static boolean isValidationRejected(TraceEvent attempt) {
        return "validation rejected".equals(attempt.stringField("failure_reason"));
    }

    private static boolean isUnsupported(TraceEvent attempt) {
        return !boolField(attempt, "accepted")
                && ("all candidates pruned".equals(attempt.stringField("failure_reason"))
                        || attempt.stringField("planner_reason").contains("unmaterialized")
                        || attempt.stringField("planner_reason").contains("no repair candidates"));
    }

    private static boolean isFullPass(TraceEvent attempt) {
        return boolField(attempt, "full_pass");
    }

    private static boolean boolField(TraceEvent event, String fieldName) {
        Object value = event.fields.get(fieldName);
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }

    private static void agentContext(String[] args) throws Exception {
        Path source = null;
        Path trace = null;
        Path searchReport = null;
        Path validationReport = null;
        String diagnosticId = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--source")) {
                source = Paths.get(args[++i]);
            } else if (args[i].equals("--trace")) {
                trace = Paths.get(args[++i]);
            } else if (args[i].equals("--diagnostic")) {
                diagnosticId = args[++i];
            } else if (args[i].equals("--search-report")) {
                searchReport = Paths.get(args[++i]);
            } else if (args[i].equals("--validation-report")) {
                validationReport = Paths.get(args[++i]);
            }
        }
        if (source == null || trace == null) {
            usage();
            System.exit(2);
        }
        TraceModel model = load(trace);
        if (diagnosticId == null) {
            if (model.diagnostics.isEmpty()) {
                throw new IllegalArgumentException("trace contains no diagnostics");
            }
            diagnosticId = model.diagnostics.keySet().iterator().next();
        }
        TraceModel.DiagnosticSlice slice = model.slice(diagnosticId);
        List<SuggestedRepair> repairs = new RepairPlanner().plan(source, slice);
        List<TraceEvent> searchReportEvents =
                searchReport == null
                        ? Collections.emptyList()
                        : new SearchReportParser().parse(searchReport);
        TraceEvent validationResult =
                validationReport == null
                        ? null
                        : new ValidationReportParser().parse(validationReport);
        System.out.println(
                new AgentContextJson()
                        .toJson(
                                source,
                                diagnosticId,
                                slice,
                                repairs,
                                searchReportEvents,
                                validationResult));
    }

    private static void agentRepair(String[] args) throws Exception {
        Path context = null;
        RepairKind kindFilter = null;
        RiskLevel riskFilter = null;
        boolean automaticOnly = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--context")) {
                context = Paths.get(args[++i]);
            } else if (args[i].equals("--kind")) {
                kindFilter = RepairKind.valueOf(args[++i]);
            } else if (args[i].equals("--allow-risk")) {
                riskFilter = RiskLevel.valueOf(args[++i]);
            } else if (args[i].equals("--automatic-only")) {
                automaticOnly = true;
            }
        }
        if (context == null) {
            usage();
            System.exit(2);
        }
        RepairPlanJson json = new RepairPlanJson();
        for (PlannedRepair plannedRepair : new AgentContextParser().deterministicRepairs(context)) {
            SuggestedRepair repair = plannedRepair.repair();
            if (kindFilter != null && repair.kind() != kindFilter) {
                continue;
            }
            if (riskFilter != null && repair.risk() != riskFilter) {
                continue;
            }
            if (automaticOnly && !repair.automatic()) {
                continue;
            }
            System.out.println(json.toJson(plannedRepair.diagnosticId(), repair));
        }
    }

    private static void agentPlan(String[] args) throws Exception {
        Path source = null;
        Path context = null;
        Path proposal = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--source")) {
                source = Paths.get(args[++i]);
            } else if (args[i].equals("--context")) {
                context = Paths.get(args[++i]);
            } else if (args[i].equals("--proposal")) {
                proposal = Paths.get(args[++i]);
            }
        }
        if ((source == null && context == null) || proposal == null) {
            usage();
            System.exit(2);
        }
        RepairPlanJson json = new RepairPlanJson();
        List<PlannedRepair> repairs =
                context == null
                        ? new AgentProposalParser().parse(source, proposal)
                        : new AgentProposalParser()
                                .parse(proposal, new AgentContextParser().parse(context));
        for (PlannedRepair plannedRepair : repairs) {
            System.out.println(json.toJson(plannedRepair));
        }
    }

    private static TraceModel load(Path trace) throws Exception {
        List<TraceEvent> events = new TraceParser().parse(trace);
        return TraceModel.fromEvents(events);
    }

    private static void usage() {
        System.err.println("Usage:");
        System.err.println(
                "  java checker_reconcile.Cli diagnose trace.jsonl [--source Example.java]");
        System.err.println(
                "  java checker_reconcile.Cli repair --source Example.java --trace trace.jsonl --out Patched.java");
        System.err.println(
                "  java checker_reconcile.Cli plan --source Example.java --trace trace.jsonl");
        System.err.println(
                "  java checker_reconcile.Cli plan --source Example.java --trace trace.jsonl --include-sketch-edits");
        System.err.println(
                "  java checker_reconcile.Cli apply-plan --source Example.java --plan plan.jsonl --out Patched.java");
        System.err.println(
                "  java checker_reconcile.Cli apply-plan --source Example.java --plan plan.jsonl --out Patched.java --validate --validation-mode pass --javac javac --checker checker.jar");
        System.err.println(
                "  java checker_reconcile.Cli apply-plan --source Example.java --plan plan.jsonl --out Patched.java --validate --validation-report validation.jsonl");
        System.err.println(
                "  java checker_reconcile.Cli apply-plan --source Example.java --plan plan.jsonl --out Patched.java --diagnostic E1 --kind CHANGE_QUALIFIER --allow-risk LOCAL_ONLY");
        System.err.println(
                "  java checker_reconcile.Cli apply-plan --source Example.java --plan plan.jsonl --out Patched.java --dry-run");
        System.err.println(
                "  java checker_reconcile.Cli lint-plan --source Example.java --plan plan.jsonl");
        System.err.println(
                "  java checker_reconcile.Cli search-repair --source Example.java --trace trace.jsonl --out Patched.java --validation-mode decrease --allow-risk LOCAL_ONLY --max-candidate-size 2 --max-search-candidates 100 --search-report search.jsonl --javac javac --checker checker.jar");
        System.err.println(
                "  java checker_reconcile.Cli search-repair --source Example.java --trace trace.jsonl --out Patched.java --validation-mode decrease --allow-risk LOCAL_ONLY --search-rounds 2 --javac javac --checker checker.jar");
        System.err.println(
                "  java checker_reconcile.Cli search-repair --source Example.java --trace trace.jsonl --out Patched.java --diagnostic E1 --allow-risk LOCAL_ONLY,API_CHANGE");
        System.err.println(
                "  java checker_reconcile.Cli search-repair --source Example.java --trace trace.jsonl --out Patched.java --include-sketch-edits --allow-risk BODY_CHANGE --javac javac --checker checker.jar");
        System.err.println(
                "  java checker_reconcile.Cli search-repair --source Example.java --trace trace.jsonl --out Patched.java --explain-search");
        System.err.println(
                "  java checker_reconcile.Cli repair --source Example.java --trace trace.jsonl --out Patched.java --validate --javac javac --checker checker.jar");
        System.err.println(
                "  java checker_reconcile.Cli validate --javac javac --checker checker.jar Patched.java");
        System.err.println(
                "  java checker_reconcile.Cli mine-cf-tests --root checker/tests/nullness --examples --repair-candidates --limit 20");
        System.err.println(
                "  java checker_reconcile.Cli repair-matrix --tests checker/tests/nullness-repair");
        System.err.println(
                "  java checker_reconcile.Cli corpus-report --root checker/tests/nullness --out build/reports/nullness-repair-corpus/report.jsonl --summary build/reports/nullness-repair-corpus/summary.txt --allow-risk LOCAL_ONLY,API_CHANGE --validation-mode decrease --limit 100 --javac javac --checker checker.jar");
        System.err.println(
                "  java checker_reconcile.Cli corpus-report --root checker/tests/nullness --out build/reports/nullness-repair-corpus-round2/report.jsonl --summary build/reports/nullness-repair-corpus-round2/summary.txt --allow-risk LOCAL_ONLY,API_CHANGE --validation-mode decrease --search-rounds 2 --limit 100 --javac javac --checker checker.jar");
        System.err.println(
                "  java checker_reconcile.Cli corpus-report --root checker/tests/nullness --out report.jsonl --summary summary.txt --javac-option -AsomeOption --classpath-extra tests/build/testclasses");
        System.err.println(
                "  java checker_reconcile.Cli corpus-report --root checker/tests/nullness --out report.jsonl --summary summary.txt --progress");
        System.err.println(
                "  java checker_reconcile.Cli corpus-inspect --report build/reports/nullness-repair-corpus/report.jsonl --limit 5");
        System.err.println(
                "  java checker_reconcile.Cli agent-context --source Example.java --trace trace.jsonl --diagnostic E1 --search-report search.jsonl");
        System.err.println(
                "  java checker_reconcile.Cli agent-context --source Example.java --trace trace.jsonl --validation-report validation.jsonl");
        System.err.println(
                "  java checker_reconcile.Cli agent-repair --context context.jsonl --automatic-only --allow-risk LOCAL_ONLY");
        System.err.println(
                "  java checker_reconcile.Cli agent-plan --source Example.java --proposal proposal.jsonl");
        System.err.println(
                "  java checker_reconcile.Cli agent-plan --context context.jsonl --proposal proposal.jsonl");
    }
}
