package checker_reconcile.repair;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import checker_reconcile.constraints.TraceModel.DiagnosticSlice;
import checker_reconcile.trace.TraceEvent;

/** JSON serialization for external agent repair context bundles. */
public final class AgentContextJson {
    public String toJson(
            Path source,
            String diagnosticId,
            DiagnosticSlice slice,
            List<SuggestedRepair> deterministicRepairs,
            List<TraceEvent> searchReportEvents,
            TraceEvent validationResult) {
        StringBuilder result = new StringBuilder("{");
        result.append("\"schema_version\":1");
        result.append(',');
        field(result, "event", "agent_context");
        result.append(',');
        field(result, "source", source.toString());
        result.append(',');
        field(result, "diagnostic_id", diagnosticId);
        result.append(',');
        result.append("\"diagnostic\":");
        value(result, slice.diagnostic().fields);
        result.append(',');
        result.append("\"obligation\":");
        value(result, slice.obligation().fields);
        result.append(',');
        assumptionsField(result, slice.assumptions());
        result.append(',');
        repairsField(result, deterministicRepairs);
        result.append(',');
        searchSummaryField(result, searchReportEvents);
        result.append(',');
        searchReportField(result, searchReportEvents);
        result.append(',');
        validationResultField(result, validationResult);
        return result.append('}').toString();
    }

    private void assumptionsField(StringBuilder result, Map<String, TraceEvent> assumptions) {
        result.append("\"assumptions\":[");
        int index = 0;
        for (TraceEvent assumption : assumptions.values()) {
            if (index > 0) {
                result.append(',');
            }
            value(result, assumption.fields);
            index++;
        }
        result.append(']');
    }

    private void repairsField(StringBuilder result, List<SuggestedRepair> repairs) {
        result.append("\"deterministic_repairs\":[");
        for (int i = 0; i < repairs.size(); i++) {
            if (i > 0) {
                result.append(',');
            }
            SuggestedRepair repair = repairs.get(i);
            result.append('{');
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

    private void searchReportField(StringBuilder result, List<TraceEvent> events) {
        result.append("\"search_report\":[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) {
                result.append(',');
            }
            value(result, events.get(i).fields);
        }
        result.append(']');
    }

    private void searchSummaryField(StringBuilder result, List<TraceEvent> events) {
        result.append("\"search_summary\":");
        TraceEvent summary = searchSummary(events);
        if (summary == null) {
            result.append("{}");
        } else {
            value(result, summary.fields);
        }
    }

    private TraceEvent searchSummary(List<TraceEvent> events) {
        for (TraceEvent event : events) {
            if ("summary".equals(event.stringField("event"))) {
                return event;
            }
        }
        return null;
    }

    private void validationResultField(StringBuilder result, TraceEvent validationResult) {
        result.append("\"validation_result\":");
        if (validationResult == null) {
            result.append("{}");
        } else {
            value(result, validationResult.fields);
        }
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

    @SuppressWarnings("unchecked")
    private void value(StringBuilder result, Object value) {
        if (value == null) {
            result.append("null");
        } else if (value instanceof String) {
            result.append(quote((String) value));
        } else if (value instanceof Number || value instanceof Boolean) {
            result.append(value);
        } else if (value instanceof Map<?, ?>) {
            result.append('{');
            int index = 0;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                if (index > 0) {
                    result.append(',');
                }
                field(result, entry.getKey(), entry.getValue());
                index++;
            }
            result.append('}');
        } else if (value instanceof List<?>) {
            result.append('[');
            List<Object> values = (List<Object>) value;
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    result.append(',');
                }
                value(result, values.get(i));
            }
            result.append(']');
        } else {
            result.append(quote(value.toString()));
        }
    }

    private void field(StringBuilder result, String name, Object value) {
        result.append('"').append(name).append("\":");
        value(result, value);
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
