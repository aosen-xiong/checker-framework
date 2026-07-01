package checker_reconcile.repair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import checker_reconcile.trace.TraceEvent;
import checker_reconcile.trace.TraceParser;

/** Parser and schema validator for external agent repair proposals. */
public final class AgentProposalParser {
    public List<PlannedRepair> parse(Path source, Path proposal) throws IOException {
        List<PlannedRepair> repairs = new ArrayList<>();
        for (TraceEvent event : new TraceParser().parse(proposal)) {
            repairs.add(parseProposal(source, event, null));
        }
        return repairs;
    }

    public List<PlannedRepair> parse(Path proposal, TraceEvent context) throws IOException {
        Path source = Path.of(requiredContextString(context, "source"));
        List<PlannedRepair> repairs = new ArrayList<>();
        for (TraceEvent event : new TraceParser().parse(proposal)) {
            repairs.add(parseProposal(source, event, context));
        }
        return repairs;
    }

    private PlannedRepair parseProposal(Path source, TraceEvent event, TraceEvent context) {
        requireSchemaVersion(event);
        if (!requiredString(event, "event").equals("agent_proposal")) {
            throw error(event, "event must be agent_proposal");
        }
        validateAgainstContext(event, context, source);
        SuggestedRepair repair =
                new SuggestedRepair(
                        repairKind(requiredString(event, "kind"), event),
                        edits(source, event),
                        riskLevel(requiredString(event, "risk"), event),
                        requiredBoolean(event, "automatic"),
                        stringList(requiredList(event, "evidence_ids")),
                        requiredString(event, "message"));
        boolean requiresValidation = optionalRequiresValidation(event);
        if (repair.automatic() && !repair.edits().isEmpty()) {
            requiresValidation = true;
        }
        return new PlannedRepair(
                requiredString(event, "diagnostic_id"),
                repair,
                "agent",
                optionalConfidence(event),
                requiresValidation);
    }

    private void validateAgainstContext(TraceEvent proposal, TraceEvent context, Path source) {
        if (context == null) {
            return;
        }
        String expectedDiagnosticId = requiredContextString(context, "diagnostic_id");
        String proposalDiagnosticId = requiredString(proposal, "diagnostic_id");
        if (!proposalDiagnosticId.equals(expectedDiagnosticId)) {
            throw error(
                    proposal,
                    "diagnostic_id "
                            + proposalDiagnosticId
                            + " does not match context "
                            + expectedDiagnosticId);
        }
        Set<String> evidenceIds = contextEvidenceIds(context);
        for (Object evidence : requiredList(proposal, "evidence_ids")) {
            String evidenceId = evidence.toString();
            if (!evidenceIds.contains(evidenceId)) {
                throw error(proposal, "unknown evidence_id " + evidenceId);
            }
        }
        validateEditRanges(proposal, source);
    }

    @SuppressWarnings("unchecked")
    private Set<String> contextEvidenceIds(TraceEvent context) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(requiredContextString(context, "diagnostic_id"));
        addObjectId(ids, objectField(context, "diagnostic"));
        addObjectId(ids, objectField(context, "obligation"));
        for (Object value : context.listField("assumptions")) {
            if (value instanceof Map<?, ?>) {
                addObjectId(ids, (Map<String, Object>) value);
            }
        }
        collectEvidenceIds(ids, context.listField("deterministic_repairs"));
        collectEvidenceIds(ids, context.listField("search_report"));
        return ids;
    }

    private void addObjectId(Set<String> ids, Map<String, Object> object) {
        Object id = object.get("id");
        if (id instanceof String && !((String) id).isEmpty()) {
            ids.add((String) id);
        }
    }

    @SuppressWarnings("unchecked")
    private void collectEvidenceIds(Set<String> ids, Object value) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> object = (Map<String, Object>) value;
            Object id = object.get("id");
            if (id instanceof String && !((String) id).isEmpty()) {
                ids.add((String) id);
            }
            Object evidenceIds = object.get("evidence_ids");
            if (evidenceIds instanceof List<?>) {
                for (Object evidenceId : (List<Object>) evidenceIds) {
                    ids.add(evidenceId.toString());
                }
            }
            for (Object nested : object.values()) {
                collectEvidenceIds(ids, nested);
            }
        } else if (value instanceof List<?>) {
            for (Object nested : (List<Object>) value) {
                collectEvidenceIds(ids, nested);
            }
        }
    }

    private void validateEditRanges(TraceEvent proposal, Path source) {
        long sourceLength;
        try {
            sourceLength = Files.size(source);
        } catch (IOException e) {
            throw error(proposal, "cannot read source " + source);
        }
        for (Object editValue : proposal.listField("edits")) {
            if (!(editValue instanceof Map<?, ?>)) {
                throw error(proposal, "edit must be an object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> edit = (Map<String, Object>) editValue;
            int start = intField(edit, proposal, "start_offset");
            int end = intField(edit, proposal, "end_offset");
            if (start < 0 || end < start || end > sourceLength) {
                throw error(
                        proposal,
                        "edit range "
                                + start
                                + "-"
                                + end
                                + " is outside source length "
                                + sourceLength);
            }
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

    private boolean requiredBoolean(TraceEvent event, String field) {
        Object value = event.fields.get(field);
        if (!(value instanceof Boolean)) {
            throw error(event, "missing boolean " + field);
        }
        return ((Boolean) value).booleanValue();
    }

    private List<Object> requiredList(TraceEvent event, String field) {
        Object value = event.fields.get(field);
        if (!(value instanceof List<?>)) {
            throw error(event, "missing list " + field);
        }
        return event.listField(field);
    }

    private String requiredContextString(TraceEvent event, String field) {
        Object value = event.fields.get(field);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalArgumentException("agent context missing string " + field);
        }
        return (String) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectField(TraceEvent event, String field) {
        Object value = event.fields.get(field);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("agent context missing object " + field);
        }
        return (Map<String, Object>) value;
    }

    private Double optionalConfidence(TraceEvent event) {
        Object value = event.fields.get("confidence");
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number)) {
            throw error(event, "confidence must be numeric");
        }
        double confidence = ((Number) value).doubleValue();
        if (confidence < 0.0 || confidence > 1.0) {
            throw error(event, "confidence must be between 0 and 1");
        }
        return confidence;
    }

    private boolean optionalRequiresValidation(TraceEvent event) {
        Object value = event.fields.get("requires_validation");
        if (value == null) {
            return false;
        }
        if (!(value instanceof Boolean)) {
            throw error(event, "requires_validation must be boolean");
        }
        return ((Boolean) value).booleanValue();
    }

    @SuppressWarnings("unchecked")
    private List<SourceEdit> edits(Path source, TraceEvent event) {
        List<SourceEdit> edits = new ArrayList<>();
        for (Object editValue : event.listField("edits")) {
            if (!(editValue instanceof Map<?, ?>)) {
                throw error(event, "edit must be an object");
            }
            Map<String, Object> edit = (Map<String, Object>) editValue;
            Object replacement = edit.get("replacement");
            if (!(replacement instanceof String)) {
                throw error(event, "edit missing replacement");
            }
            edits.add(
                    new SourceEdit(
                            source,
                            intField(edit, event, "start_offset"),
                            intField(edit, event, "end_offset"),
                            (String) replacement));
        }
        return edits;
    }

    private int intField(Map<String, Object> fields, TraceEvent event, String name) {
        Object value = fields.get(name);
        if (!(value instanceof Number)) {
            throw error(event, "edit missing numeric " + name);
        }
        return ((Number) value).intValue();
    }

    private RepairKind repairKind(String value, TraceEvent event) {
        try {
            return RepairKind.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw error(event, "invalid kind " + value);
        }
    }

    private RiskLevel riskLevel(String value, TraceEvent event) {
        try {
            return RiskLevel.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw error(event, "invalid risk " + value);
        }
    }

    private List<String> stringList(List<Object> values) {
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            result.add(value.toString());
        }
        return result;
    }

    private IllegalArgumentException error(TraceEvent event, String message) {
        return new IllegalArgumentException(
                "agent proposal line " + event.lineNumber + ": " + message);
    }
}
