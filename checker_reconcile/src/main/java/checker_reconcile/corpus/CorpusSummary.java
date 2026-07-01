package checker_reconcile.corpus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import checker_reconcile.repair.RepairKind;

/** Aggregated corpus repair-evaluation counts. */
public final class CorpusSummary {
    private final int total;
    private final int traceOk;
    private final int searchOk;
    private final int accepted;
    private final int decreased;
    private final int fullPass;
    private final int validationCacheHits;
    private final int validationCacheMisses;
    private final Map<String, Integer> byDiagnosticKind;
    private final Map<String, Integer> byPossibleRepairKind;
    private final Map<String, Integer> byPossibleRepairKindOutcome;
    private final Map<String, String> byPossibleRepairKindOutcomeExample;
    private final Map<String, Integer> byAcceptedRisk;
    private final Map<String, Integer> byAcceptedRepairKind;
    private final Map<String, Integer> byAcceptedEdit;
    private final Map<String, Integer> byFailureReason;
    private final Map<String, Integer> byPlannerReason;
    private final Map<String, Integer> byAgentRefactorContext;
    private final Map<String, String> byOutcomeExample;
    private final Map<String, String> byAgentRefactorContextExample;

    public CorpusSummary(List<CorpusAttempt> attempts) {
        int traceOkCount = 0;
        int searchOkCount = 0;
        int acceptedCount = 0;
        int decreasedCount = 0;
        int fullPassCount = 0;
        int cacheHits = 0;
        int cacheMisses = 0;
        Map<String, Integer> diagnosticKinds = new LinkedHashMap<>();
        Map<String, Integer> possibleRepairKinds = new LinkedHashMap<>();
        Map<String, Integer> possibleRepairKindOutcomes = new LinkedHashMap<>();
        Map<String, String> possibleRepairKindOutcomeExamples = new LinkedHashMap<>();
        Map<String, Integer> acceptedRisks = new LinkedHashMap<>();
        Map<String, Integer> acceptedRepairKinds = new LinkedHashMap<>();
        Map<String, Integer> acceptedEdits = new LinkedHashMap<>();
        Map<String, Integer> failureReasons = new LinkedHashMap<>();
        Map<String, Integer> plannerReasons = new LinkedHashMap<>();
        Map<String, Integer> agentRefactorContexts = new LinkedHashMap<>();
        Map<String, String> outcomeExamples = new LinkedHashMap<>();
        Map<String, String> agentRefactorContextExamples = new LinkedHashMap<>();
        for (CorpusAttempt attempt : attempts) {
            increment(diagnosticKinds, attempt.corpusCase().diagnosticKind());
            for (RepairKind repairKind : attempt.corpusCase().possibleRepairKinds()) {
                String outcome = outcomeForPossibleRepairKind(attempt, repairKind);
                String outcomeKey = repairKind.name() + "/" + outcome;
                increment(possibleRepairKinds, repairKind.name());
                increment(possibleRepairKindOutcomes, outcomeKey);
                possibleRepairKindOutcomeExamples.putIfAbsent(outcomeKey, exampleKey(attempt));
            }
            if (attempt.traceOk()) {
                traceOkCount++;
            }
            if (attempt.searchOk()) {
                searchOkCount++;
            }
            if (attempt.accepted()) {
                acceptedCount++;
            }
            if (attempt.decreased()) {
                decreasedCount++;
            }
            if (attempt.fullPass()) {
                fullPassCount++;
            }
            if (attempt.validationCacheHit()) {
                cacheHits++;
            }
            if (attempt.validationCacheMiss()) {
                cacheMisses++;
            }
            if (attempt.acceptedRisk() != null) {
                increment(acceptedRisks, attempt.acceptedRisk().name());
            }
            if (attempt.acceptedRepairKind() != null) {
                increment(acceptedRepairKinds, attempt.acceptedRepairKind().name());
            }
            for (CorpusEdit edit : attempt.acceptedEdits()) {
                increment(acceptedEdits, attempt.corpusCase().relativeSource() + " " + edit.key());
            }
            if (!attempt.failureReason().isEmpty()) {
                increment(failureReasons, attempt.failureReason());
            }
            if (!attempt.plannerReason().isEmpty()) {
                increment(plannerReasons, attempt.plannerReason());
            }
            if (attempt.agentRefactorTarget()) {
                increment(agentRefactorContexts, attempt.agentRefactorContext());
                agentRefactorContextExamples.putIfAbsent(
                        attempt.agentRefactorContext(), exampleKey(attempt));
            }
            outcomeExamples.putIfAbsent(outcomeBucket(attempt), exampleKey(attempt));
        }
        this.total = attempts.size();
        this.traceOk = traceOkCount;
        this.searchOk = searchOkCount;
        this.accepted = acceptedCount;
        this.decreased = decreasedCount;
        this.fullPass = fullPassCount;
        this.validationCacheHits = cacheHits;
        this.validationCacheMisses = cacheMisses;
        this.byDiagnosticKind = Collections.unmodifiableMap(diagnosticKinds);
        this.byPossibleRepairKind = Collections.unmodifiableMap(possibleRepairKinds);
        this.byPossibleRepairKindOutcome = Collections.unmodifiableMap(possibleRepairKindOutcomes);
        this.byPossibleRepairKindOutcomeExample =
                Collections.unmodifiableMap(possibleRepairKindOutcomeExamples);
        this.byAcceptedRisk = Collections.unmodifiableMap(acceptedRisks);
        this.byAcceptedRepairKind = Collections.unmodifiableMap(acceptedRepairKinds);
        this.byAcceptedEdit = Collections.unmodifiableMap(acceptedEdits);
        this.byFailureReason = Collections.unmodifiableMap(failureReasons);
        this.byPlannerReason = Collections.unmodifiableMap(plannerReasons);
        this.byAgentRefactorContext = Collections.unmodifiableMap(agentRefactorContexts);
        this.byOutcomeExample = Collections.unmodifiableMap(outcomeExamples);
        this.byAgentRefactorContextExample =
                Collections.unmodifiableMap(agentRefactorContextExamples);
    }

    private void increment(Map<String, Integer> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }

    private String outcomeForPossibleRepairKind(CorpusAttempt attempt, RepairKind repairKind) {
        if (attempt.accepted() && repairKind == attempt.acceptedRepairKind()) {
            return "accepted";
        }
        if (!attempt.plannerReason().isEmpty()) {
            return attempt.plannerReason();
        }
        if (!attempt.failureReason().isEmpty()) {
            return attempt.failureReason();
        }
        if (attempt.accepted()) {
            return "different repair accepted";
        }
        return "unknown";
    }

    private String exampleKey(CorpusAttempt attempt) {
        if (attempt.corpusCase().diagnosticId().isEmpty()) {
            return attempt.corpusCase().relativeSource();
        }
        return attempt.corpusCase().relativeSource() + "#" + attempt.corpusCase().diagnosticId();
    }

    private String outcomeBucket(CorpusAttempt attempt) {
        if (attempt.fullPass()) {
            return "full_pass";
        }
        if (attempt.accepted() && attempt.decreased()) {
            return "decreased_not_full_pass";
        }
        if (attempt.accepted()) {
            return "accepted_without_decrease";
        }
        if (attempt.agentRefactorTarget()) {
            return "agent_refactor_target";
        }
        if (!attempt.failureReason().isEmpty()) {
            return "failure:" + attempt.failureReason();
        }
        return "unsupported";
    }

    public int total() {
        return total;
    }

    public int traceOk() {
        return traceOk;
    }

    public int searchOk() {
        return searchOk;
    }

    public int accepted() {
        return accepted;
    }

    public int decreased() {
        return decreased;
    }

    public int fullPass() {
        return fullPass;
    }

    public int validationCacheHits() {
        return validationCacheHits;
    }

    public int validationCacheMisses() {
        return validationCacheMisses;
    }

    public int uniqueValidatedPatches() {
        return validationCacheMisses;
    }

    public Map<String, Integer> byDiagnosticKind() {
        return byDiagnosticKind;
    }

    public Map<String, Integer> byPossibleRepairKind() {
        return byPossibleRepairKind;
    }

    public Map<String, Integer> byPossibleRepairKindOutcome() {
        return byPossibleRepairKindOutcome;
    }

    public Map<String, String> byPossibleRepairKindOutcomeExample() {
        return byPossibleRepairKindOutcomeExample;
    }

    public Map<String, Integer> byAcceptedRisk() {
        return byAcceptedRisk;
    }

    public Map<String, Integer> byAcceptedRepairKind() {
        return byAcceptedRepairKind;
    }

    public Map<String, Integer> byAcceptedEdit() {
        return byAcceptedEdit;
    }

    public Map<String, Integer> byFailureReason() {
        return byFailureReason;
    }

    public Map<String, Integer> byPlannerReason() {
        return byPlannerReason;
    }

    public Map<String, Integer> byAgentRefactorContext() {
        return byAgentRefactorContext;
    }

    public Map<String, String> byOutcomeExample() {
        return byOutcomeExample;
    }

    public Map<String, String> byAgentRefactorContextExample() {
        return byAgentRefactorContextExample;
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        out.append("total: ").append(total).append('\n');
        out.append("trace-ok: ").append(traceOk).append('\n');
        out.append("search-ok: ").append(searchOk).append('\n');
        out.append("accepted: ").append(accepted).append('\n');
        out.append("decreased: ").append(decreased).append('\n');
        out.append("full-pass: ").append(fullPass).append('\n');
        out.append("validation-cache-hits: ").append(validationCacheHits).append('\n');
        out.append("unique-validated-patches: ").append(uniqueValidatedPatches()).append('\n');
        appendCounts(out, "diagnostic-kind", byDiagnosticKind);
        appendCounts(out, "possible-repair-kind", byPossibleRepairKind);
        appendCounts(out, "possible-repair-kind-outcome", byPossibleRepairKindOutcome);
        appendStringMap(
                out, "possible-repair-kind-outcome-example", byPossibleRepairKindOutcomeExample);
        appendStringMap(out, "outcome-example", byOutcomeExample);
        appendCounts(out, "accepted-risk", byAcceptedRisk);
        appendCounts(out, "accepted-repair-kind", byAcceptedRepairKind);
        appendCounts(out, "accepted-edit", byAcceptedEdit);
        appendCounts(out, "failure-reason", byFailureReason);
        appendCounts(out, "planner-reason", byPlannerReason);
        appendCounts(out, "agent-refactor-context", byAgentRefactorContext);
        appendStringMap(out, "agent-refactor-context-example", byAgentRefactorContextExample);
        return out.toString();
    }

    private void appendCounts(StringBuilder out, String title, Map<String, Integer> counts) {
        out.append(title).append(":\n");
        if (counts.isEmpty()) {
            out.append("  <none>: 0\n");
            return;
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            out.append("  ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append('\n');
        }
    }

    private void appendStringMap(StringBuilder out, String title, Map<String, String> values) {
        out.append(title).append(":\n");
        if (values.isEmpty()) {
            out.append("  <none>: \n");
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out.append("  ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append('\n');
        }
    }
}
