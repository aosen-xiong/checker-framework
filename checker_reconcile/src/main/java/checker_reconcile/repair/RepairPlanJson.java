package checker_reconcile.repair;

import java.util.List;

/** JSON serialization for machine-readable repair plans. */
public final class RepairPlanJson {
    public String toJson(String diagnosticId, SuggestedRepair repair) {
        return toJson(new PlannedRepair(diagnosticId, repair));
    }

    public String toJson(PlannedRepair plannedRepair) {
        SuggestedRepair repair = plannedRepair.repair();
        StringBuilder result = new StringBuilder("{");
        result.append("\"schema_version\":1");
        result.append(',');
        field(result, "diagnostic_id", plannedRepair.diagnosticId());
        if (!plannedRepair.origin().equals("deterministic")
                || plannedRepair.confidence() != null
                || plannedRepair.requiresValidation()) {
            result.append(',');
            field(result, "origin", plannedRepair.origin());
        }
        if (plannedRepair.confidence() != null) {
            result.append(',');
            result.append("\"confidence\":").append(plannedRepair.confidence());
        }
        if (plannedRepair.requiresValidation()) {
            result.append(',');
            result.append("\"requires_validation\":true");
        }
        result.append(',');
        field(result, "kind", repair.kind().name());
        result.append(',');
        field(result, "risk", repair.risk().name());
        result.append(',');
        result.append("\"automatic\":").append(repair.automatic());
        result.append(',');
        field(result, "message", repair.message());
        result.append(',');
        stringArrayField(result, "evidence_ids", repair.evidenceIds());
        result.append(',');
        editsField(result, repair.edits());
        result.append(',');
        sketchesField(result, repair.sketches());
        return result.append('}').toString();
    }

    private void sketchesField(StringBuilder result, List<RepairSketch> sketches) {
        result.append("\"sketches\":[");
        for (int i = 0; i < sketches.size(); i++) {
            if (i > 0) {
                result.append(',');
            }
            sketchField(result, sketches.get(i));
        }
        result.append(']');
    }

    private void sketchField(StringBuilder result, RepairSketch sketch) {
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
