package checker_reconcile.diagnosis;

import java.util.List;
import java.util.Map;

import checker_reconcile.repair.AgentRefactorTargetJson;
import checker_reconcile.repair.RepairSketch;
import checker_reconcile.repair.SourceEdit;
import checker_reconcile.repair.SourceTargetJson;
import checker_reconcile.repair.SuggestedRepair;
import checker_reconcile.repair.Validation;

/** JSONL serialization for validation-backed repair search attempts. */
public final class SearchReportJson {
    public String skipped(int index, RepairCandidateSet candidateSet, String reason) {
        StringBuilder result = candidatePrefix("candidate_skipped", index, candidateSet);
        result.append(',');
        field(result, "reason", reason);
        return result.append('}').toString();
    }

    public String pruned(int index, RepairCandidateSet candidateSet, String reason) {
        StringBuilder result = candidatePrefix("candidate_pruned", index, candidateSet);
        result.append(',');
        field(result, "reason", reason);
        return result.append('}').toString();
    }

    public String invalid(int index, RepairCandidateSet candidateSet, String reason) {
        StringBuilder result = candidatePrefix("candidate_invalid", index, candidateSet);
        result.append(',');
        field(result, "reason", reason);
        return result.append('}').toString();
    }

    public String validated(
            int index, RepairCandidateSet candidateSet, Validation.Result after, boolean accepted) {
        StringBuilder result = candidatePrefix("candidate_validated", index, candidateSet);
        result.append(',');
        result.append("\"accepted\":").append(accepted);
        result.append(',');
        result.append("\"after_diagnostic_count\":").append(after.diagnosticCount());
        result.append(',');
        result.append("\"after_exit_code\":").append(after.exitCode());
        return result.append('}').toString();
    }

    public String summary(ValidationBackedRepairSearch.Result result) {
        StringBuilder out = new StringBuilder("{");
        out.append("\"schema_version\":1");
        out.append(',');
        field(out, "event", "summary");
        out.append(',');
        out.append("\"accepted\":").append(result.accepted());
        out.append(',');
        out.append("\"before_diagnostic_count\":").append(result.before().diagnosticCount());
        out.append(',');
        out.append("\"before_exit_code\":").append(result.before().exitCode());
        out.append(',');
        out.append("\"after_diagnostic_count\":").append(result.after().diagnosticCount());
        out.append(',');
        out.append("\"after_exit_code\":").append(result.after().exitCode());
        ValidationBackedRepairSearch.SearchStats stats = result.searchStats();
        out.append(',');
        out.append("\"max_candidate_size\":").append(stats.maxCandidateSize());
        out.append(',');
        out.append("\"max_search_candidates\":").append(stats.maxSearchCandidates());
        out.append(',');
        out.append("\"generated_candidate_count\":").append(stats.generatedCandidateCount());
        out.append(',');
        out.append("\"searched_candidate_count\":").append(stats.searchedCandidateCount());
        out.append(',');
        out.append("\"pruned_empty_edit_count\":").append(stats.prunedEmptyEditCount());
        out.append(',');
        numberMapField(out, "pruned_empty_edit_reasons", stats.prunedEmptyEditReasons());
        out.append(',');
        out.append("\"pruned_duplicate_edit_count\":").append(stats.prunedDuplicateEditCount());
        out.append(',');
        out.append("\"pruned_overlap_count\":").append(stats.prunedOverlapCount());
        out.append(',');
        out.append("\"pruned_budget_count\":").append(stats.prunedBudgetCount());
        out.append(',');
        stringArrayField(out, "all_diagnostic_ids", stats.allDiagnosticIds());
        out.append(',');
        stringArrayField(out, "validated_diagnostic_ids", stats.validatedDiagnosticIds());
        out.append(',');
        stringArrayField(out, "accepted_diagnostic_ids", stats.acceptedDiagnosticIds());
        out.append(',');
        stringArrayField(out, "rejected_diagnostic_ids", stats.rejectedDiagnosticIds());
        out.append(',');
        stringArrayField(out, "skipped_diagnostic_ids", stats.skippedDiagnosticIds());
        out.append(',');
        stringArrayField(out, "uncovered_diagnostic_ids", stats.uncoveredDiagnosticIds());
        if (result.candidateSet() != null) {
            out.append(',');
            out.append("\"accepted_candidate_cost\":").append(result.candidateSet().cost().value());
            out.append(',');
            out.append("\"accepted_candidate_size\":")
                    .append(result.candidateSet().candidates().size());
        }
        return out.append('}').toString();
    }

    private StringBuilder candidatePrefix(
            String event, int index, RepairCandidateSet candidateSet) {
        StringBuilder result = new StringBuilder("{");
        result.append("\"schema_version\":1");
        result.append(',');
        field(result, "event", event);
        result.append(',');
        result.append("\"candidate_index\":").append(index);
        result.append(',');
        result.append("\"candidate_cost\":").append(candidateSet.cost().value());
        result.append(',');
        result.append("\"candidate_size\":").append(candidateSet.candidates().size());
        result.append(',');
        stringArrayField(result, "diagnostic_ids", candidateSet.diagnosticIds());
        result.append(',');
        repairsField(result, candidateSet.candidates());
        return result;
    }

    private void repairsField(StringBuilder result, List<RepairCandidate> candidates) {
        result.append("\"repairs\":[");
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 0) {
                result.append(',');
            }
            RepairCandidate candidate = candidates.get(i);
            SuggestedRepair repair = candidate.repair();
            result.append('{');
            field(result, "kind", repair.kind().name());
            result.append(',');
            field(result, "risk", repair.risk().name());
            result.append(',');
            result.append("\"automatic\":").append(repair.automatic());
            result.append(',');
            result.append("\"cost\":").append(candidate.cost().value());
            result.append(',');
            stringArrayField(result, "diagnostic_ids", candidate.diagnosticIds());
            result.append(',');
            stringArrayField(result, "assumption_ids", candidate.assumptionIds());
            result.append(',');
            stringArrayField(result, "evidence_ids", repair.evidenceIds());
            result.append(',');
            editsField(result, repair.edits());
            result.append(',');
            sketchesField(result, repair.sketches());
            result.append('}');
        }
        result.append(']');
    }

    private void sketchesField(StringBuilder result, List<RepairSketch> sketches) {
        result.append("\"sketches\":[");
        for (int i = 0; i < sketches.size(); i++) {
            if (i > 0) {
                result.append(',');
            }
            RepairSketch sketch = sketches.get(i);
            result.append('{');
            field(result, "kind", sketch.kind());
            result.append(',');
            field(result, "target_id", sketch.targetId());
            result.append(',');
            result.append("\"automatic\":").append(sketch.automatic());
            result.append(',');
            field(result, "message", sketch.message());
            if (!sketch.materializationFailure().isEmpty()) {
                result.append(',');
                field(result, "materialization_failure", sketch.materializationFailure());
                AgentRefactorTargetJson.append(result, sketch.materializationFailure());
            }
            if (!sketch.sourceTargetKind().isEmpty()) {
                result.append(',');
                SourceTargetJson.append(result, sketch);
            }
            result.append('}');
        }
        result.append(']');
    }

    private void editsField(StringBuilder result, List<SourceEdit> edits) {
        result.append("\"edits\":[");
        for (int i = 0; i < edits.size(); i++) {
            if (i > 0) {
                result.append(',');
            }
            SourceEdit edit = edits.get(i);
            result.append('{');
            field(result, "file", edit.file().toString());
            result.append(',');
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

    private void numberMapField(StringBuilder result, String name, Map<String, Integer> values) {
        result.append('"').append(name).append("\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (!first) {
                result.append(',');
            }
            result.append(quote(entry.getKey())).append(':').append(entry.getValue());
            first = false;
        }
        result.append('}');
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
}
