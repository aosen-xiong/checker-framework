package checker_reconcile.diagnosis;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import checker_reconcile.trace.TraceEvent;
import checker_reconcile.trace.TraceParser;

/** Parser and schema validator for validation-backed search JSONL reports. */
public final class SearchReportParser {
    public List<TraceEvent> parse(Path report) throws IOException {
        List<TraceEvent> events = new TraceParser().parse(report);
        for (TraceEvent event : events) {
            validate(event);
        }
        return events;
    }

    private void validate(TraceEvent event) {
        requireSchemaVersion(event);
        String kind = requiredString(event, "event");
        if (kind.equals("candidate_validated")) {
            requireCandidateFields(event);
            requireBoolean(event, "accepted");
            requireNumber(event, "after_diagnostic_count");
            requireNumber(event, "after_exit_code");
        } else if (kind.equals("candidate_skipped")
                || kind.equals("candidate_invalid")
                || kind.equals("candidate_pruned")) {
            requireCandidateFields(event);
            requiredString(event, "reason");
        } else if (kind.equals("summary")) {
            requireBoolean(event, "accepted");
            requireNumber(event, "before_diagnostic_count");
            requireNumber(event, "before_exit_code");
            requireNumber(event, "after_diagnostic_count");
            requireNumber(event, "after_exit_code");
            requireNumber(event, "max_candidate_size");
            requireNumber(event, "max_search_candidates");
            requireNumber(event, "generated_candidate_count");
            requireNumber(event, "searched_candidate_count");
            requireNumber(event, "pruned_empty_edit_count");
            requireMap(event, "pruned_empty_edit_reasons");
            requireNumber(event, "pruned_duplicate_edit_count");
            requireNumber(event, "pruned_overlap_count");
            requireNumber(event, "pruned_budget_count");
            requireList(event, "all_diagnostic_ids");
            requireList(event, "validated_diagnostic_ids");
            requireList(event, "accepted_diagnostic_ids");
            requireList(event, "rejected_diagnostic_ids");
            requireList(event, "skipped_diagnostic_ids");
            requireList(event, "uncovered_diagnostic_ids");
        } else {
            throw error(event, "invalid event " + kind);
        }
    }

    private void requireCandidateFields(TraceEvent event) {
        requireNumber(event, "candidate_index");
        requireNumber(event, "candidate_cost");
        requireNumber(event, "candidate_size");
        if (event.listField("diagnostic_ids").isEmpty()) {
            throw error(event, "candidate event requires non-empty diagnostic_ids");
        }
        if (event.listField("repairs").isEmpty()) {
            throw error(event, "candidate event requires non-empty repairs");
        }
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

    private void requireNumber(TraceEvent event, String field) {
        if (!(event.fields.get(field) instanceof Number)) {
            throw error(event, "missing numeric " + field);
        }
    }

    private void requireBoolean(TraceEvent event, String field) {
        if (!(event.fields.get(field) instanceof Boolean)) {
            throw error(event, "missing boolean " + field);
        }
    }

    private void requireList(TraceEvent event, String field) {
        if (!(event.fields.get(field) instanceof List<?>)) {
            throw error(event, "missing list " + field);
        }
    }

    private void requireMap(TraceEvent event, String field) {
        if (!(event.fields.get(field) instanceof java.util.Map<?, ?>)) {
            throw error(event, "missing map " + field);
        }
    }

    private IllegalArgumentException error(TraceEvent event, String message) {
        return new IllegalArgumentException(
                "search report line " + event.lineNumber + ": " + message);
    }
}
