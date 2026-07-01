package checker_reconcile.repair;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** JSON serialization for machine-readable validation outcomes. */
public final class ValidationReportJson {
    public String toJson(
            Path source,
            Path patchedSource,
            String validationMode,
            boolean accepted,
            Validation.Result before,
            Validation.Result after,
            List<PlannedRepair> plannedRepairs) {
        StringBuilder result = new StringBuilder("{");
        result.append("\"schema_version\":1");
        result.append(',');
        field(result, "event", "validation_result");
        result.append(',');
        field(result, "source", source.toString());
        result.append(',');
        field(result, "patched_source", patchedSource.toString());
        result.append(',');
        field(result, "validation_mode", validationMode);
        result.append(',');
        result.append("\"accepted\":").append(accepted);
        result.append(',');
        result.append("\"applied_plan_count\":").append(plannedRepairs.size());
        result.append(',');
        result.append("\"applied_edit_plan_count\":").append(appliedEditPlanCount(plannedRepairs));
        result.append(',');
        stringArrayField(result, "diagnostic_ids", diagnosticIds(plannedRepairs));
        result.append(',');
        result.append("\"agent_origin_count\":").append(agentOriginCount(plannedRepairs));
        result.append(',');
        result.append("\"validation_required_count\":")
                .append(validationRequiredCount(plannedRepairs));
        if (before != null) {
            result.append(',');
            result.append("\"before_exit_code\":").append(before.exitCode());
            result.append(',');
            result.append("\"before_diagnostic_count\":").append(before.diagnosticCount());
        }
        if (after != null) {
            result.append(',');
            result.append("\"after_exit_code\":").append(after.exitCode());
            result.append(',');
            result.append("\"after_diagnostic_count\":").append(after.diagnosticCount());
        }
        return result.append('}').toString();
    }

    private Set<String> diagnosticIds(List<PlannedRepair> plannedRepairs) {
        Set<String> ids = new LinkedHashSet<>();
        for (PlannedRepair plannedRepair : plannedRepairs) {
            ids.add(plannedRepair.diagnosticId());
        }
        return ids;
    }

    private int agentOriginCount(List<PlannedRepair> plannedRepairs) {
        int count = 0;
        for (PlannedRepair plannedRepair : plannedRepairs) {
            if (plannedRepair.origin().equals("agent")) {
                count++;
            }
        }
        return count;
    }

    private int appliedEditPlanCount(List<PlannedRepair> plannedRepairs) {
        int count = 0;
        for (PlannedRepair plannedRepair : plannedRepairs) {
            SuggestedRepair repair = plannedRepair.repair();
            if (repair.automatic() && !repair.edits().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int validationRequiredCount(List<PlannedRepair> plannedRepairs) {
        int count = 0;
        for (PlannedRepair plannedRepair : plannedRepairs) {
            if (plannedRepair.requiresValidation()) {
                count++;
            }
        }
        return count;
    }

    private void stringArrayField(StringBuilder result, String name, Set<String> values) {
        result.append('"').append(name).append("\":[");
        int index = 0;
        for (String value : values) {
            if (index > 0) {
                result.append(',');
            }
            result.append(quote(value));
            index++;
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
