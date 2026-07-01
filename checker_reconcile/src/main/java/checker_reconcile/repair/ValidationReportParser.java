package checker_reconcile.repair;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import checker_reconcile.trace.TraceEvent;
import checker_reconcile.trace.TraceParser;

/** Parser and schema validator for validation-result reports. */
public final class ValidationReportParser {
    public TraceEvent parse(Path report) throws IOException {
        List<TraceEvent> events = new TraceParser().parse(report);
        if (events.size() != 1) {
            throw new IllegalArgumentException(
                    "validation report must contain exactly one JSON object");
        }
        TraceEvent event = events.get(0);
        validate(event);
        return event;
    }

    public void validate(TraceEvent event) {
        requireSchemaVersion(event);
        String kind = requiredString(event, "event");
        if (!kind.equals("validation_result")) {
            throw error(event, "invalid event " + kind);
        }
        requiredString(event, "source");
        requiredString(event, "patched_source");
        String mode = requiredString(event, "validation_mode");
        if (!mode.equals("pass") && !mode.equals("decrease")) {
            throw error(event, "invalid validation_mode " + mode);
        }
        requiredBoolean(event, "accepted");
        requiredNumber(event, "applied_plan_count");
        requiredNumber(event, "applied_edit_plan_count");
        requireList(event, "diagnostic_ids");
        requiredNumber(event, "agent_origin_count");
        requiredNumber(event, "validation_required_count");
        if (mode.equals("decrease")) {
            requiredNumber(event, "before_exit_code");
            requiredNumber(event, "before_diagnostic_count");
        }
        requiredNumber(event, "after_exit_code");
        requiredNumber(event, "after_diagnostic_count");
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

    private void requiredBoolean(TraceEvent event, String field) {
        if (!(event.fields.get(field) instanceof Boolean)) {
            throw error(event, "missing boolean " + field);
        }
    }

    private void requiredNumber(TraceEvent event, String field) {
        if (!(event.fields.get(field) instanceof Number)) {
            throw error(event, "missing numeric " + field);
        }
    }

    private void requireList(TraceEvent event, String field) {
        if (!(event.fields.get(field) instanceof List<?>)) {
            throw error(event, "missing list " + field);
        }
    }

    private IllegalArgumentException error(TraceEvent event, String message) {
        return new IllegalArgumentException(
                "validation report line " + event.lineNumber + ": " + message);
    }
}
