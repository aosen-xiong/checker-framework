package checker_reconcile.corpus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import checker_reconcile.diagnosis.ValidationBackedRepairSearch;
import checker_reconcile.repair.RepairKind;
import checker_reconcile.repair.RiskLevel;
import checker_reconcile.repair.Validation;
import checker_reconcile.trace.TraceEvent;

/** JSONL serialization for corpus repair reports. */
public final class CorpusReportJson {
    public String attempt(CorpusAttempt attempt) {
        StringBuilder out = new StringBuilder("{");
        out.append("\"schema_version\":1,");
        field(out, "event", "corpus_attempt");
        out.append(',');
        field(out, "source", attempt.corpusCase().relativeSource());
        out.append(',');
        field(out, "diagnostic_id", attempt.corpusCase().diagnosticId());
        out.append(',');
        field(out, "diagnostic_kind", attempt.corpusCase().diagnosticKind());
        out.append(',');
        field(out, "original_source", attempt.corpusCase().source().toString());
        out.append(',');
        field(out, "trace_path", pathString(attempt.tracePath()));
        out.append(',');
        field(out, "work_source", pathString(attempt.workSourcePath()));
        out.append(',');
        field(out, "patched_source", pathString(attempt.patchedSourcePath()));
        out.append(',');
        repairKindArrayField(
                out, "possible_repair_kinds", attempt.corpusCase().possibleRepairKinds());
        out.append(',');
        out.append("\"trace_ok\":").append(attempt.traceOk());
        out.append(',');
        out.append("\"search_ok\":").append(attempt.searchOk());
        out.append(',');
        out.append("\"accepted\":").append(attempt.accepted());
        out.append(',');
        out.append("\"decreased\":").append(attempt.decreased());
        out.append(',');
        out.append("\"full_pass\":").append(attempt.fullPass());
        out.append(',');
        field(out, "failure_reason", attempt.failureReason());
        out.append(',');
        field(out, "planner_reason", attempt.plannerReason());
        out.append(',');
        out.append("\"agent_refactor_target\":").append(attempt.agentRefactorTarget());
        out.append(',');
        field(out, "agent_refactor_context", attempt.agentRefactorContext());
        out.append(',');
        stringMapField(out, "options", attempt.corpusCase().options());
        out.append(',');
        riskArrayField(out, "allowed_risks", attempt.allowedRisks());
        out.append(',');
        field(
                out,
                "accepted_risk",
                attempt.acceptedRisk() == null ? "" : attempt.acceptedRisk().name());
        out.append(',');
        field(
                out,
                "accepted_repair_kind",
                attempt.acceptedRepairKind() == null ? "" : attempt.acceptedRepairKind().name());
        out.append(',');
        editArrayField(out, "accepted_edits", attempt.acceptedEdits());
        out.append(',');
        out.append("\"validation_cache_hit\":").append(attempt.validationCacheHit());
        out.append(',');
        out.append("\"validation_cache_miss\":").append(attempt.validationCacheMiss());
        appendValidation(out, attempt.before(), attempt.after());
        appendSearchStats(out, attempt.searchStats());
        return out.append('}').toString();
    }

    public String summary(CorpusSummary summary) {
        StringBuilder out = new StringBuilder("{");
        out.append("\"schema_version\":1,");
        field(out, "event", "corpus_summary");
        out.append(',');
        out.append("\"total\":").append(summary.total());
        out.append(',');
        out.append("\"trace_ok\":").append(summary.traceOk());
        out.append(',');
        out.append("\"search_ok\":").append(summary.searchOk());
        out.append(',');
        out.append("\"accepted\":").append(summary.accepted());
        out.append(',');
        out.append("\"decreased\":").append(summary.decreased());
        out.append(',');
        out.append("\"full_pass\":").append(summary.fullPass());
        out.append(',');
        out.append("\"validation_cache_hits\":").append(summary.validationCacheHits());
        out.append(',');
        out.append("\"unique_validated_patches\":").append(summary.uniqueValidatedPatches());
        out.append(',');
        numberMapField(out, "by_diagnostic_kind", summary.byDiagnosticKind());
        out.append(',');
        numberMapField(out, "by_possible_repair_kind", summary.byPossibleRepairKind());
        out.append(',');
        numberMapField(
                out, "by_possible_repair_kind_outcome", summary.byPossibleRepairKindOutcome());
        out.append(',');
        stringMapField(
                out,
                "by_possible_repair_kind_outcome_example",
                summary.byPossibleRepairKindOutcomeExample());
        out.append(',');
        stringMapField(out, "by_outcome_example", summary.byOutcomeExample());
        out.append(',');
        stringMapField(
                out, "by_agent_refactor_context_example", summary.byAgentRefactorContextExample());
        out.append(',');
        numberMapField(out, "by_accepted_risk", summary.byAcceptedRisk());
        out.append(',');
        numberMapField(out, "by_accepted_repair_kind", summary.byAcceptedRepairKind());
        out.append(',');
        numberMapField(out, "by_accepted_edit", summary.byAcceptedEdit());
        out.append(',');
        numberMapField(out, "by_failure_reason", summary.byFailureReason());
        out.append(',');
        numberMapField(out, "by_planner_reason", summary.byPlannerReason());
        out.append(',');
        numberMapField(out, "by_agent_refactor_context", summary.byAgentRefactorContext());
        return out.append('}').toString();
    }

    public List<TraceEvent> parse(java.nio.file.Path report) throws java.io.IOException {
        return new checker_reconcile.trace.TraceParser().parse(report);
    }

    private void appendValidation(
            StringBuilder out, Validation.Result before, Validation.Result after) {
        out.append(',');
        out.append("\"before_diagnostic_count\":")
                .append(before == null ? -1 : before.diagnosticCount());
        out.append(',');
        out.append("\"before_exit_code\":").append(before == null ? -1 : before.exitCode());
        out.append(',');
        out.append("\"after_diagnostic_count\":")
                .append(after == null ? -1 : after.diagnosticCount());
        out.append(',');
        out.append("\"after_exit_code\":").append(after == null ? -1 : after.exitCode());
    }

    private void appendSearchStats(
            StringBuilder out, ValidationBackedRepairSearch.SearchStats stats) {
        out.append(',');
        out.append("\"generated_candidate_count\":")
                .append(stats == null ? 0 : stats.generatedCandidateCount());
        out.append(',');
        out.append("\"searched_candidate_count\":")
                .append(stats == null ? 0 : stats.searchedCandidateCount());
        out.append(',');
        out.append("\"pruned_empty_edit_count\":")
                .append(stats == null ? 0 : stats.prunedEmptyEditCount());
        out.append(',');
        out.append("\"pruned_duplicate_edit_count\":")
                .append(stats == null ? 0 : stats.prunedDuplicateEditCount());
        out.append(',');
        out.append("\"pruned_overlap_count\":")
                .append(stats == null ? 0 : stats.prunedOverlapCount());
        out.append(',');
        out.append("\"pruned_budget_count\":")
                .append(stats == null ? 0 : stats.prunedBudgetCount());
    }

    private void riskArrayField(StringBuilder out, String name, Iterable<RiskLevel> risks) {
        List<String> values = new ArrayList<>();
        for (RiskLevel risk : risks) {
            values.add(risk.name());
        }
        stringArrayField(out, name, values);
    }

    private void repairKindArrayField(
            StringBuilder out, String name, Iterable<RepairKind> repairKinds) {
        List<String> values = new ArrayList<>();
        for (RepairKind repairKind : repairKinds) {
            values.add(repairKind.name());
        }
        stringArrayField(out, name, values);
    }

    private void editArrayField(StringBuilder out, String name, List<CorpusEdit> edits) {
        out.append('"').append(name).append("\":[");
        for (int i = 0; i < edits.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            CorpusEdit edit = edits.get(i);
            out.append('{');
            out.append("\"start_offset\":").append(edit.startOffset());
            out.append(',');
            out.append("\"end_offset\":").append(edit.endOffset());
            out.append(',');
            field(out, "original", edit.original());
            out.append(',');
            field(out, "replacement", edit.replacement());
            out.append('}');
        }
        out.append(']');
    }

    private void stringArrayField(StringBuilder out, String name, List<String> values) {
        out.append('"').append(name).append("\":[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(quote(values.get(i)));
        }
        out.append(']');
    }

    private void numberMapField(StringBuilder out, String name, Map<String, Integer> values) {
        out.append('"').append(name).append("\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(quote(entry.getKey())).append(':').append(entry.getValue());
        }
        out.append('}');
    }

    private void stringMapField(StringBuilder out, String name, Map<String, String> values) {
        out.append('"').append(name).append("\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
        }
        out.append('}');
    }

    private void field(StringBuilder out, String name, String value) {
        out.append('"').append(name).append("\":").append(quote(value));
    }

    private String pathString(java.nio.file.Path path) {
        return path == null ? "" : path.toString();
    }

    private String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                    break;
            }
        }
        return out.append('"').toString();
    }
}
