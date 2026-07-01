package checker_reconcile.repair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Converts simple ADD_NULL_CHECK sketches into non-automatic statement insertions. */
public final class NullCheckEditPlanner {
    public List<SuggestedRepair> addNullCheckEdits(Path source, List<SuggestedRepair> repairs)
            throws IOException {
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        List<SuggestedRepair> result = new ArrayList<>();
        for (SuggestedRepair repair : repairs) {
            if (repair.kind() != RepairKind.ADD_NULL_CHECK || !repair.edits().isEmpty()) {
                result.add(repair);
                continue;
            }
            Materialization materialization = firstNullCheckEdit(source, text, repair.sketches());
            if (materialization.edit == null) {
                result.add(
                        new SuggestedRepair(
                                repair.kind(),
                                repair.edits(),
                                repair.risk(),
                                repair.automatic(),
                                repair.evidenceIds(),
                                repair.message(),
                                materialization.sketches));
                continue;
            }
            List<SourceEdit> edits = new ArrayList<>();
            edits.add(materialization.edit);
            result.add(
                    new SuggestedRepair(
                            repair.kind(),
                            edits,
                            repair.risk(),
                            false,
                            repair.evidenceIds(),
                            repair.message(),
                            materialization.sketches));
        }
        return result;
    }

    private Materialization firstNullCheckEdit(
            Path source, String text, List<RepairSketch> sketches) {
        List<RepairSketch> annotatedSketches = new ArrayList<>();
        SourceEdit accepted = null;
        for (RepairSketch sketch : sketches) {
            Attempt attempt = nullCheckEdit(source, text, sketch);
            if (attempt.edit != null && accepted == null) {
                accepted = attempt.edit;
                annotatedSketches.add(sketch);
            } else {
                annotatedSketches.add(sketch.withMaterializationFailure(attempt.reason));
            }
        }
        return new Materialization(accepted, annotatedSketches);
    }

    private Attempt nullCheckEdit(Path source, String text, RepairSketch sketch) {
        if (!"add_null_check".equals(sketch.kind())
                || sketch.expression().isEmpty()
                || sketch.startOffset() == null
                || sketch.endOffset() == null) {
            return Attempt.rejected("missing expression range");
        }
        String expression = sketch.expression();
        if (!"receiver_expression".equals(sketch.sourceTargetKind())
                && !"array_expression".equals(sketch.sourceTargetKind())
                && !"condition_expression".equals(sketch.sourceTargetKind())
                && !"iteration_expression".equals(sketch.sourceTargetKind())
                && !"argument_expression".equals(sketch.sourceTargetKind())) {
            return Attempt.rejected("unsupported source_target kind: " + sketch.sourceTargetKind());
        }
        int expressionStart = sketch.startOffset();
        if (expressionStart < 0 || expressionStart > text.length()) {
            return Attempt.rejected("invalid expression range");
        }
        if (sketch.endOffset() < expressionStart || sketch.endOffset() > text.length()) {
            return Attempt.rejected("invalid expression range");
        }
        String guardExpression = simpleGuardExpression(expression);
        if (guardExpression.isEmpty()) {
            return Attempt.rejected("non-simple expression");
        }
        int lineStart = lineStart(text, expressionStart);
        int lineEnd = lineEnd(text, expressionStart);
        String unsupportedReason =
                unsupportedStatementUseReason(
                        text.substring(lineStart, lineEnd), guardExpression, sketch);
        if (!unsupportedReason.isEmpty()) {
            return Attempt.rejected(unsupportedReason);
        }
        int insertionOffset = insertionOffset(text, sketch, lineStart);
        String indentation = indentationAt(text, lineStart(text, insertionOffset));
        String innerIndentation = indentation + "    ";
        String check =
                indentation
                        + "if ("
                        + guardExpression
                        + " == null) {"
                        + System.lineSeparator()
                        + innerIndentation
                        + "throw new NullPointerException(\""
                        + escapeJava(guardExpression)
                        + "\");"
                        + System.lineSeparator()
                        + indentation
                        + "}"
                        + System.lineSeparator();
        return Attempt.accepted(new SourceEdit(source, insertionOffset, insertionOffset, check));
    }

    @SuppressWarnings("unchecked")
    private int insertionOffset(String text, RepairSketch sketch, int fallbackOffset) {
        Object statementRange = sketch.sourceTargetAttributes().get("statement_range");
        if (statementRange instanceof java.util.Map<?, ?>) {
            Object startOffset =
                    ((java.util.Map<String, Object>) statementRange).get("start_offset");
            if (startOffset instanceof Number) {
                int offset = ((Number) startOffset).intValue();
                if (offset >= 0 && offset <= text.length()) {
                    return offset;
                }
            }
        }
        return fallbackOffset;
    }

    private String simpleGuardExpression(String expression) {
        String candidate = stripBalancedParentheses(expression.trim());
        return isSimpleExpression(candidate) ? candidate : "";
    }

    private String stripBalancedParentheses(String expression) {
        String candidate = expression;
        while (candidate.startsWith("(") && candidate.endsWith(")")) {
            String inner = candidate.substring(1, candidate.length() - 1).trim();
            if (inner.isEmpty() || !hasBalancedParentheses(inner)) {
                break;
            }
            candidate = inner;
        }
        return candidate;
    }

    private boolean hasBalancedParentheses(String expression) {
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char ch = expression.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    private boolean isSimpleExpression(String expression) {
        String identifier = "[A-Za-z_$][A-Za-z0-9_$]*";
        String dottedIdentifier = identifier + "(\\." + identifier + ")*";
        String arrayIndex = "\\[(" + identifier + "|[0-9]+)\\]";
        return expression.matches(dottedIdentifier + "(" + arrayIndex + ")*");
    }

    private String unsupportedStatementUseReason(
            String line, String expression, RepairSketch sketch) {
        String sourceTargetKind = sketch.sourceTargetKind();
        String trimmed = line.trim();
        if ("receiver_expression".equals(sourceTargetKind)) {
            if (trimmed.startsWith(expression + ".") && trimmed.endsWith(";")) {
                return "";
            }
            return statementContextReason(trimmed, expression);
        }
        if ("array_expression".equals(sourceTargetKind)) {
            if (!trimmed.startsWith("return ")
                    && trimmed.contains(expression + "[")
                    && trimmed.endsWith(";")) {
                return "";
            }
            return statementContextReason(trimmed, expression);
        }
        if ("condition_expression".equals(sourceTargetKind)) {
            if (trimmed.startsWith("if (" + expression + ")")
                    || trimmed.startsWith("while (" + expression + ")")) {
                return "";
            }
            return statementContextReason(trimmed, expression);
        }
        if ("iteration_expression".equals(sourceTargetKind)) {
            if (trimmed.startsWith("for (") && trimmed.contains(" : " + expression + ")")) {
                return "";
            }
            return statementContextReason(trimmed, expression);
        }
        if ("argument_expression".equals(sourceTargetKind)) {
            return unsupportedArgumentStatementReason(trimmed, expression, sketch);
        }
        return "unsupported source_target kind: " + sourceTargetKind;
    }

    private String unsupportedArgumentStatementReason(
            String trimmed, String expression, RepairSketch sketch) {
        if (sketch.hasSourceTargetAttribute("standalone_invocation")) {
            String invocationKind = sketch.stringSourceTargetAttribute("invocation_kind");
            if ("this_constructor".equals(invocationKind)
                    || "super_constructor".equals(invocationKind)) {
                return "constructor delegation";
            }
            if (sketch.booleanSourceTargetAttribute("standalone_invocation")) {
                return "";
            }
            return statementContextReason(trimmed, expression);
        }
        if (!trimmed.endsWith(";")) {
            return statementContextReason(trimmed, expression);
        }
        if (trimmed.startsWith("return ")) {
            return "return expression";
        }
        if (trimmed.startsWith("throw ")) {
            return "throw expression";
        }
        if (trimmed.startsWith("this(") || trimmed.startsWith("super(")) {
            return "constructor delegation";
        }
        if (trimmed.contains(" -> ")) {
            return "lambda expression";
        }
        int paren = trimmed.indexOf('(');
        if (paren <= 0 || !isInvocationTarget(trimmed.substring(0, paren).trim())) {
            return statementContextReason(trimmed, expression);
        }
        int closeParen = trimmed.lastIndexOf(')');
        if (closeParen < paren || !trimmed.substring(closeParen + 1).equals(";")) {
            return statementContextReason(trimmed, expression);
        }
        String arguments = trimmed.substring(paren + 1, closeParen);
        return containsArgument(arguments, expression) ? "" : "argument expression not found";
    }

    private String statementContextReason(String trimmed, String expression) {
        if (trimmed.startsWith("return ")) {
            return "return expression";
        }
        if (trimmed.startsWith("throw ")) {
            return "throw expression";
        }
        int expressionIndex = trimmed.indexOf(expression);
        int equalsIndex = trimmed.indexOf('=');
        if (equalsIndex >= 0 && (expressionIndex < 0 || equalsIndex < expressionIndex)) {
            return "nested expression";
        }
        return "unsafe insertion context";
    }

    private boolean isInvocationTarget(String value) {
        String identifier = "[A-Za-z_$][A-Za-z0-9_$]*";
        return value.matches("(" + identifier + "\\.)*" + identifier);
    }

    private boolean containsArgument(String arguments, String expression) {
        int index = arguments.indexOf(expression);
        while (index >= 0) {
            int before = previousNonWhitespace(arguments, index);
            int after = nextNonWhitespace(arguments, index + expression.length());
            boolean startsArgument = before < 0 || arguments.charAt(before) == ',';
            boolean endsArgument = after < 0 || arguments.charAt(after) == ',';
            if (startsArgument && endsArgument) {
                return true;
            }
            index = arguments.indexOf(expression, index + expression.length());
        }
        return false;
    }

    private int previousNonWhitespace(String text, int startExclusive) {
        for (int i = startExclusive - 1; i >= 0; i--) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private int nextNonWhitespace(String text, int startInclusive) {
        for (int i = startInclusive; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private int lineStart(String text, int offset) {
        int newline = text.lastIndexOf('\n', Math.max(0, offset - 1));
        return newline < 0 ? 0 : newline + 1;
    }

    private int lineEnd(String text, int offset) {
        int newline = text.indexOf('\n', offset);
        return newline < 0 ? text.length() : newline;
    }

    private String indentationAt(String text, int lineStart) {
        int index = lineStart;
        while (index < text.length()) {
            char ch = text.charAt(index);
            if (ch != ' ' && ch != '\t') {
                break;
            }
            index++;
        }
        return text.substring(lineStart, index);
    }

    private String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class Materialization {
        private final SourceEdit edit;
        private final List<RepairSketch> sketches;

        private Materialization(SourceEdit edit, List<RepairSketch> sketches) {
            this.edit = edit;
            this.sketches = sketches;
        }
    }

    private static final class Attempt {
        private final SourceEdit edit;
        private final String reason;

        private Attempt(SourceEdit edit, String reason) {
            this.edit = edit;
            this.reason = reason;
        }

        private static Attempt accepted(SourceEdit edit) {
            return new Attempt(edit, "");
        }

        private static Attempt rejected(String reason) {
            return new Attempt(null, reason);
        }
    }
}
