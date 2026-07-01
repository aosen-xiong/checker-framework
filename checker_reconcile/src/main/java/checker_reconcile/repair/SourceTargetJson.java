package checker_reconcile.repair;

import java.util.List;
import java.util.Map;

/** JSON helper for sketch source targets. */
public final class SourceTargetJson {
    private SourceTargetJson() {}

    public static void append(StringBuilder result, RepairSketch sketch) {
        result.append("\"source_target\":{");
        field(result, "kind", sketch.sourceTargetKind());
        if (!sketch.expression().isEmpty()) {
            result.append(',');
            field(result, "expression", sketch.expression());
        }
        if (sketch.startOffset() != null && sketch.endOffset() != null) {
            result.append(',');
            result.append("\"expression_range\":{");
            result.append("\"start_offset\":").append(sketch.startOffset());
            result.append(',');
            result.append("\"end_offset\":").append(sketch.endOffset());
            result.append('}');
        }
        for (Map.Entry<String, Object> entry : sketch.sourceTargetAttributes().entrySet()) {
            result.append(',');
            fieldName(result, entry.getKey());
            result.append(':');
            value(result, entry.getValue());
        }
        result.append('}');
    }

    @SuppressWarnings("unchecked")
    private static void value(StringBuilder result, Object value) {
        if (value == null) {
            result.append("null");
        } else if (value instanceof String) {
            fieldValue(result, (String) value);
        } else if (value instanceof Boolean || value instanceof Number) {
            result.append(value);
        } else if (value instanceof Map<?, ?>) {
            result.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                if (!first) {
                    result.append(',');
                }
                first = false;
                fieldName(result, entry.getKey());
                result.append(':');
                value(result, entry.getValue());
            }
            result.append('}');
        } else if (value instanceof List<?>) {
            result.append('[');
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) {
                    result.append(',');
                }
                first = false;
                value(result, item);
            }
            result.append(']');
        } else {
            fieldValue(result, value.toString());
        }
    }

    private static void field(StringBuilder result, String name, String value) {
        fieldName(result, name);
        result.append(':');
        fieldValue(result, value);
    }

    private static void fieldName(StringBuilder result, String name) {
        fieldValue(result, name);
    }

    private static void fieldValue(StringBuilder result, String value) {
        result.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"' || ch == '\\') {
                result.append('\\');
            }
            result.append(ch);
        }
        result.append('"');
    }
}
