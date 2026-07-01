package checker_reconcile.repair;

/** Shared JSON fragment for sketches that should be handed to an external refactor agent. */
public final class AgentRefactorTargetJson {
    private AgentRefactorTargetJson() {}

    public static void append(StringBuilder result, String materializationFailure) {
        String context = refactorContext(materializationFailure);
        if (context.isEmpty()) {
            return;
        }
        result.append(',');
        result.append("\"agent_refactor_target\":true");
        result.append(',');
        field(result, "refactor_context", context);
    }

    public static String refactorContext(String materializationFailure) {
        if (materializationFailure == null) {
            return "";
        }
        if (materializationFailure.contains("constructor delegation")) {
            return "constructor_delegation";
        }
        if (materializationFailure.contains("nested expression")) {
            return "nested_expression";
        }
        if (materializationFailure.contains("return expression")) {
            return "return_expression";
        }
        if (materializationFailure.contains("throw expression")) {
            return "throw_expression";
        }
        if (materializationFailure.contains("lambda expression")) {
            return "lambda_expression";
        }
        return "";
    }

    private static void field(StringBuilder result, String name, String value) {
        result.append('"').append(name).append("\":").append(quote(value));
    }

    private static String quote(String value) {
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
