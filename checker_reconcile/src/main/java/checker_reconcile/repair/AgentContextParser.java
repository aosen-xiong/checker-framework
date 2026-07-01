package checker_reconcile.repair;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import checker_reconcile.trace.TraceEvent;
import checker_reconcile.trace.TraceParser;

/** Parser and schema validator for external agent repair context bundles. */
public final class AgentContextParser {
    public TraceEvent parse(Path context) throws IOException {
        List<TraceEvent> events = new TraceParser().parse(context);
        if (events.size() != 1) {
            throw new IllegalArgumentException(
                    "agent context must contain exactly one JSON object");
        }
        TraceEvent event = events.get(0);
        validate(event);
        return event;
    }

    public List<PlannedRepair> deterministicRepairs(Path context) throws IOException {
        TraceEvent event = parse(context);
        String diagnosticId = requiredString(event, "diagnostic_id");
        Path source = Paths.get(requiredString(event, "source"));
        List<PlannedRepair> repairs = new ArrayList<>();
        for (Object repairValue : event.listField("deterministic_repairs")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> repair = (Map<String, Object>) repairValue;
            repairs.add(new PlannedRepair(diagnosticId, suggestedRepair(source, event, repair)));
        }
        return repairs;
    }

    private void validate(TraceEvent event) {
        requireSchemaVersion(event);
        String kind = requiredString(event, "event");
        if (!kind.equals("agent_context")) {
            throw error(event, "invalid event " + kind);
        }
        requiredString(event, "source");
        requiredString(event, "diagnostic_id");
        requireObject(event, "diagnostic");
        requireObject(event, "obligation");
        requireList(event, "assumptions");
        requireList(event, "deterministic_repairs");
        requireObject(event, "search_summary");
        requireList(event, "search_report");
        requireObject(event, "validation_result");
        requireTraceObject(event, "diagnostic");
        requireTraceObject(event, "obligation");
        requireRepairObjects(event);
        requireSearchSummaryObject(event);
        requireSearchReportObjects(event);
        requireValidationResultObject(event);
    }

    private void requireTraceObject(TraceEvent event, String field) {
        Map<String, Object> object = objectField(event, field);
        Object eventValue = object.get("event");
        Object idValue = object.get("id");
        if (!(eventValue instanceof String) || ((String) eventValue).isEmpty()) {
            throw error(event, field + " missing event");
        }
        if (!(idValue instanceof String) || ((String) idValue).isEmpty()) {
            throw error(event, field + " missing id");
        }
    }

    @SuppressWarnings("unchecked")
    private void requireRepairObjects(TraceEvent event) {
        for (Object repairValue : event.listField("deterministic_repairs")) {
            if (!(repairValue instanceof Map<?, ?>)) {
                throw error(event, "deterministic repair must be an object");
            }
            Map<String, Object> repair = (Map<String, Object>) repairValue;
            requireString(repair, event, "kind");
            requireString(repair, event, "risk");
            if (!(repair.get("automatic") instanceof Boolean)) {
                throw error(event, "deterministic repair missing boolean automatic");
            }
            requireString(repair, event, "message");
            requireList(repair, event, "evidence_ids");
            requireList(repair, event, "edits");
        }
    }

    @SuppressWarnings("unchecked")
    private void requireSearchReportObjects(TraceEvent event) {
        for (Object searchEventValue : event.listField("search_report")) {
            if (!(searchEventValue instanceof Map<?, ?>)) {
                throw error(event, "search_report event must be an object");
            }
            Map<String, Object> searchEvent = (Map<String, Object>) searchEventValue;
            Object schema = searchEvent.get("schema_version");
            if (!(schema instanceof Number) || ((Number) schema).intValue() != 1) {
                throw error(event, "search_report event requires schema_version 1");
            }
            requireString(searchEvent, event, "event");
        }
    }

    private void requireSearchSummaryObject(TraceEvent event) {
        Map<String, Object> summary = objectField(event, "search_summary");
        if (summary.isEmpty()) {
            return;
        }
        Object summaryEvent = summary.get("event");
        if (!"summary".equals(summaryEvent)) {
            throw error(event, "search_summary event must be summary");
        }
        requireList(summary, event, "all_diagnostic_ids");
        requireList(summary, event, "validated_diagnostic_ids");
        requireList(summary, event, "accepted_diagnostic_ids");
        requireList(summary, event, "rejected_diagnostic_ids");
        requireList(summary, event, "skipped_diagnostic_ids");
        requireList(summary, event, "uncovered_diagnostic_ids");
    }

    private void requireValidationResultObject(TraceEvent event) {
        Map<String, Object> validationResult = objectField(event, "validation_result");
        if (validationResult.isEmpty()) {
            return;
        }
        new ValidationReportParser().validate(new TraceEvent(event.lineNumber, validationResult));
        String diagnosticId = requiredString(event, "diagnostic_id");
        if (!listField(validationResult, event, "diagnostic_ids").contains(diagnosticId)) {
            throw error(
                    event,
                    "validation_result diagnostic_ids must include context diagnostic "
                            + diagnosticId);
        }
    }

    private SuggestedRepair suggestedRepair(
            Path source, TraceEvent event, Map<String, Object> repair) {
        return new SuggestedRepair(
                repairKind(stringField(repair, event, "kind")),
                sourceEdits(source, event, repair),
                riskLevel(stringField(repair, event, "risk")),
                booleanField(repair, event, "automatic"),
                stringList(listField(repair, event, "evidence_ids")),
                stringField(repair, event, "message"),
                repairSketches(event, repair));
    }

    @SuppressWarnings("unchecked")
    private List<SourceEdit> sourceEdits(
            Path defaultSource, TraceEvent event, Map<String, Object> repair) {
        List<SourceEdit> edits = new ArrayList<>();
        for (Object editValue : listField(repair, event, "edits")) {
            if (!(editValue instanceof Map<?, ?>)) {
                throw error(event, "repair edit must be an object");
            }
            Map<String, Object> edit = (Map<String, Object>) editValue;
            Path file =
                    edit.get("file") instanceof String
                            ? Paths.get((String) edit.get("file"))
                            : defaultSource;
            edits.add(
                    new SourceEdit(
                            file,
                            intField(edit, event, "start_offset"),
                            intField(edit, event, "end_offset"),
                            stringField(edit, event, "replacement")));
        }
        return edits;
    }

    @SuppressWarnings("unchecked")
    private List<RepairSketch> repairSketches(TraceEvent event, Map<String, Object> repair) {
        Object sketchesValue = repair.get("sketches");
        if (sketchesValue == null) {
            return new ArrayList<>();
        }
        if (!(sketchesValue instanceof List<?>)) {
            throw error(event, "repair sketches must be a list");
        }
        List<RepairSketch> sketches = new ArrayList<>();
        for (Object sketchValue : (List<Object>) sketchesValue) {
            if (!(sketchValue instanceof Map<?, ?>)) {
                throw error(event, "repair sketch must be an object");
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
                    startOffset = intField(range, event, "start_offset");
                    endOffset = intField(range, event, "end_offset");
                    if (startOffset < 0 || endOffset < startOffset) {
                        throw error(event, "repair sketch has invalid range");
                    }
                }
            } else if (sourceTargetValue != null) {
                throw error(event, "repair sketch source_target must be an object");
            }
            sketches.add(
                    new RepairSketch(
                            stringValue(sketch.get("kind")),
                            stringValue(sketch.get("target_id")),
                            booleanField(sketch, event, "automatic"),
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

    private RepairKind repairKind(String value) {
        try {
            return RepairKind.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("agent context repair invalid kind: " + value, e);
        }
    }

    private RiskLevel riskLevel(String value) {
        try {
            return RiskLevel.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("agent context repair invalid risk: " + value, e);
        }
    }

    private List<String> stringList(List<Object> values) {
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            result.add(value.toString());
        }
        return result;
    }

    private void requireSchemaVersion(TraceEvent event) {
        Object value = event.fields.get("schema_version");
        if (!(value instanceof Number) || ((Number) value).intValue() != 1) {
            throw error(event, "requires schema_version 1");
        }
    }

    private String requiredString(TraceEvent event, String field) {
        Object value = event.fields.get(field);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw error(event, "missing string " + field);
        }
        return (String) value;
    }

    private void requireObject(TraceEvent event, String field) {
        if (!(event.fields.get(field) instanceof Map<?, ?>)) {
            throw error(event, "missing object " + field);
        }
    }

    private void requireList(TraceEvent event, String field) {
        if (!(event.fields.get(field) instanceof List<?>)) {
            throw error(event, "missing list " + field);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectField(TraceEvent event, String field) {
        return (Map<String, Object>) event.fields.get(field);
    }

    private void requireString(Map<String, Object> object, TraceEvent event, String field) {
        Object value = object.get(field);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw error(event, "missing string " + field);
        }
    }

    private void requireList(Map<String, Object> object, TraceEvent event, String field) {
        if (!(object.get(field) instanceof List<?>)) {
            throw error(event, "missing list " + field);
        }
    }

    private String stringField(Map<String, Object> object, TraceEvent event, String field) {
        Object value = object.get(field);
        if (!(value instanceof String)) {
            throw error(event, "missing string " + field);
        }
        return (String) value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean booleanField(Map<String, Object> object, TraceEvent event, String field) {
        Object value = object.get(field);
        if (!(value instanceof Boolean)) {
            throw error(event, "missing boolean " + field);
        }
        return ((Boolean) value).booleanValue();
    }

    @SuppressWarnings("unchecked")
    private List<Object> listField(Map<String, Object> object, TraceEvent event, String field) {
        Object value = object.get(field);
        if (!(value instanceof List<?>)) {
            throw error(event, "missing list " + field);
        }
        return (List<Object>) value;
    }

    private int intField(Map<String, Object> object, TraceEvent event, String field) {
        Object value = object.get(field);
        if (!(value instanceof Number)) {
            throw error(event, "missing numeric " + field);
        }
        return ((Number) value).intValue();
    }

    private IllegalArgumentException error(TraceEvent event, String message) {
        return new IllegalArgumentException(
                "agent context line " + event.lineNumber + ": " + message);
    }
}
