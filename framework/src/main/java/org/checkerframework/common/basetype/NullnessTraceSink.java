package org.checkerframework.common.basetype;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;

import org.checkerframework.framework.source.SourceChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.javacutil.UserError;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/** JSONL trace sink for the Nullness Checker reconciliation prototype. */
final class NullnessTraceSink implements Closeable {

    static final String OPTION = "exportNullnessTrace";

    private static final NullnessTraceSink NOOP = new NullnessTraceSink();

    private final BufferedWriter writer;
    private int nextAssumptionId = 1;
    private int nextObligationId = 1;
    private int nextDiagnosticId = 1;

    private NullnessTraceSink() {
        this.writer = null;
    }

    private NullnessTraceSink(BufferedWriter writer) {
        this.writer = writer;
    }

    static NullnessTraceSink create(SourceChecker checker) {
        if (!checker.hasOption(OPTION)) {
            return NOOP;
        }
        if (!checker.getClass()
                .getName()
                .equals("org.checkerframework.checker.nullness.NullnessNoInitSubchecker")) {
            return NOOP;
        }
        String pathString = checker.getOption(OPTION);
        if (pathString == null || pathString.isEmpty()) {
            throw new UserError("-A" + OPTION + " requires a non-empty output path");
        }
        Path path = Paths.get(pathString);
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return new NullnessTraceSink(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UserError(
                    "Could not open Nullness trace file '%s': %s", path, e.getMessage());
        }
    }

    boolean isEnabled() {
        return writer != null;
    }

    void emitSubtypeObligation(
            String kind,
            String errorKind,
            AnnotatedTypeMirror got,
            AnnotatedTypeMirror want,
            String actualSlot,
            String expectedSlot,
            String message,
            boolean success,
            Tree actualTree,
            Tree expectedTree,
            Tree invocationTree,
            TreePath invocationPath,
            int argumentIndex,
            Object formalParameter,
            CompilationUnitTree root,
            SourcePositions positions) {
        if (!isEnabled()) {
            return;
        }

        String assumption1 = "A" + nextAssumptionId++;
        String assumption2 = "A" + nextAssumptionId++;
        String obligation = "O" + nextObligationId++;
        String diagnostic = success ? null : "E" + nextDiagnosticId++;

        Map<String, Object> actual =
                baseEvent("assumption", assumption1, actualTree, root, positions);
        actual.put(
                "kind",
                actualSlot.startsWith("receiver:") ? "receiver_qualifier" : "actual_qualifier");
        actual.put("slot", actualSlot);
        actual.put("type", got.toString());
        actual.put("editable", editableSlot(actualSlot));
        actual.put("weight", editableSlot(actualSlot) ? 5 : 1000);
        Map<String, Object> actualSourceTarget =
                expressionSourceTarget(
                        kind,
                        actualSlot,
                        actualTree,
                        invocationTree,
                        invocationPath,
                        argumentIndex,
                        formalParameter,
                        root,
                        positions);
        if (!actualSourceTarget.isEmpty()) {
            actual.put("source_target", actualSourceTarget);
        }
        write(actual);

        Map<String, Object> expected =
                baseEvent("assumption", assumption2, expectedTree, root, positions);
        expected.put(
                "kind",
                expectedSlot.startsWith("receiver:") || expectedSlot.startsWith("method-contract:")
                        ? "receiver_contract"
                        : expectedSlot.startsWith("nonnull-contract:")
                                ? "nonnull_contract"
                                : "target_qualifier");
        expected.put("slot", expectedSlot);
        expected.put("type", want.toString());
        expected.put("editable", editableSlot(expectedSlot));
        expected.put("weight", editableSlot(expectedSlot) ? 5 : 1000);
        Map<String, Object> expectedSourceTarget =
                sourceTarget(expectedSlot, want, expectedTree, root, positions);
        if (!expectedSourceTarget.isEmpty()) {
            expected.put("source_target", expectedSourceTarget);
        }
        write(expected);

        Map<String, Object> obligationEvent =
                baseEvent("obligation", obligation, actualTree, root, positions);
        obligationEvent.put("kind", kind);
        obligationEvent.put(
                "relation",
                kind.equals("dereference")
                        ? "receiver_nonnull"
                        : expectedSlot.startsWith("nonnull-contract:") ? "nonnull" : "subtype");
        obligationEvent.put("got", got.toString());
        obligationEvent.put("want", want.toString());
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("actual", actualSlot);
        slots.put("expected", expectedSlot);
        obligationEvent.put("slots", slots);
        obligationEvent.put("dependencies", new String[] {assumption1, assumption2});
        Map<String, Object> actualRange = sourceRange(actualTree, root, positions);
        if (!actualRange.isEmpty()) {
            obligationEvent.put("actual_range", actualRange);
        }
        Map<String, Object> expectedRange = sourceRange(expectedTree, root, positions);
        if (!expectedRange.isEmpty()) {
            obligationEvent.put("expected_range", expectedRange);
        }
        obligationEvent.put("result", success ? "ok" : "error");
        if (diagnostic != null) {
            obligationEvent.put("diagnostic_id", diagnostic);
        }
        write(obligationEvent);

        if (diagnostic != null) {
            Map<String, Object> diagnosticEvent =
                    baseEvent("diagnostic", diagnostic, actualTree, root, positions);
            diagnosticEvent.put("error_kind", errorKind);
            diagnosticEvent.put("message", message);
            diagnosticEvent.put("obligation", obligation);
            write(diagnosticEvent);
        }
    }

    private boolean editableSlot(String slot) {
        return !(slot.startsWith("receiver:") || slot.startsWith("method-contract:"));
    }

    private Map<String, Object> baseEvent(
            String event,
            String id,
            Tree tree,
            CompilationUnitTree root,
            SourcePositions positions) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event", event);
        result.put("id", id);
        if (root != null && root.getSourceFile() != null) {
            result.put("file", root.getSourceFile().getName());
        }
        Map<String, Object> range = sourceRange(tree, root, positions);
        if (!range.isEmpty()) {
            result.put("range", range);
        }
        return result;
    }

    private Map<String, Object> sourceRange(
            Tree tree, CompilationUnitTree root, SourcePositions positions) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (tree == null || root == null || positions == null) {
            return result;
        }
        long start = positions.getStartPosition(root, tree);
        long end = positions.getEndPosition(root, tree);
        if (start < 0) {
            return result;
        }
        LineMap lineMap = root.getLineMap();
        result.put("start_offset", start);
        result.put("start_line", lineMap.getLineNumber(start));
        result.put("start_col", lineMap.getColumnNumber(start));
        if (end >= 0) {
            result.put("end_offset", end);
            result.put("end_line", lineMap.getLineNumber(end));
            result.put("end_col", lineMap.getColumnNumber(end));
        }
        return result;
    }

    private Map<String, Object> sourceTarget(
            String slot,
            AnnotatedTypeMirror type,
            Tree tree,
            CompilationUnitTree root,
            SourcePositions positions) {
        Map<String, Object> result = new LinkedHashMap<>();
        String targetKind = sourceTargetKind(slot, tree);
        if (targetKind.isEmpty()) {
            return result;
        }
        String typeString = type.toString();
        for (AnnotationTree annotationTree : annotationTrees(tree)) {
            String annotation = annotationTree.toString();
            if (!typeString.contains(annotation)) {
                continue;
            }
            Map<String, Object> annotationRange = sourceRange(annotationTree, root, positions);
            if (annotationRange.isEmpty()) {
                continue;
            }
            result.put("kind", targetKind);
            result.put("annotation", annotation);
            result.put("annotation_range", annotationRange);
            Map<String, Object> declarationRange = sourceRange(tree, root, positions);
            if (!declarationRange.isEmpty()) {
                result.put("declaration_range", declarationRange);
            }
            return result;
        }
        return result;
    }

    private Map<String, Object> expressionSourceTarget(
            String obligationKind,
            String slot,
            Tree tree,
            Tree invocationTree,
            TreePath invocationPath,
            int argumentIndex,
            Object formalParameter,
            CompilationUnitTree root,
            SourcePositions positions) {
        Map<String, Object> result = new LinkedHashMap<>();
        String targetKind = expressionSourceTargetKind(obligationKind, slot);
        if (targetKind.isEmpty()) {
            return result;
        }
        Map<String, Object> expressionRange = sourceRange(tree, root, positions);
        if (expressionRange.isEmpty()) {
            return result;
        }
        result.put("kind", targetKind);
        result.put("expression", tree.toString());
        result.put("expression_range", expressionRange);
        if (targetKind.equals("argument_expression")) {
            addInvocationContext(
                    result,
                    invocationTree,
                    invocationPath,
                    argumentIndex,
                    formalParameter,
                    root,
                    positions);
        }
        return result;
    }

    private void addInvocationContext(
            Map<String, Object> result,
            Tree invocationTree,
            TreePath invocationPath,
            int argumentIndex,
            Object formalParameter,
            CompilationUnitTree root,
            SourcePositions positions) {
        if (argumentIndex >= 0) {
            result.put("argument_index", argumentIndex);
        }
        if (formalParameter != null) {
            result.put("formal_parameter", formalParameter.toString());
        }
        if (invocationTree != null) {
            result.put("invocation_kind", invocationKind(invocationTree));
            Map<String, Object> invocationRange = sourceRange(invocationTree, root, positions);
            if (!invocationRange.isEmpty()) {
                result.put("invocation_range", invocationRange);
            }
        }
        Tree statementTree = enclosingExpressionStatement(invocationTree, invocationPath);
        result.put("standalone_invocation", statementTree != null);
        if (statementTree != null) {
            Map<String, Object> statementRange = sourceRange(statementTree, root, positions);
            if (!statementRange.isEmpty()) {
                result.put("statement_range", statementRange);
            }
        }
    }

    private String invocationKind(Tree invocationTree) {
        if (invocationTree instanceof NewClassTree) {
            return "constructor";
        }
        if (invocationTree instanceof MethodInvocationTree) {
            String methodSelect =
                    ((MethodInvocationTree) invocationTree).getMethodSelect().toString();
            if (methodSelect.equals("this")) {
                return "this_constructor";
            }
            if (methodSelect.equals("super")) {
                return "super_constructor";
            }
            return "method";
        }
        return "";
    }

    private Tree enclosingExpressionStatement(Tree invocationTree, TreePath invocationPath) {
        if (invocationTree == null
                || invocationPath == null
                || invocationPath.getParentPath() == null) {
            return null;
        }
        Tree parent = invocationPath.getParentPath().getLeaf();
        if (parent instanceof ExpressionStatementTree
                && ((ExpressionStatementTree) parent).getExpression() == invocationTree) {
            return parent;
        }
        return null;
    }

    private String expressionSourceTargetKind(String obligationKind, String slot) {
        if (slot.startsWith("receiver:")) {
            return "receiver_expression";
        }
        if (obligationKind.equals("method_argument") && slot.startsWith("expr:")) {
            return "argument_expression";
        }
        if (slot.startsWith("condition:")) {
            return "condition_expression";
        }
        if (slot.startsWith("unboxing:")) {
            return "unboxing_expression";
        }
        if (slot.startsWith("array_access:")) {
            return "array_expression";
        }
        if (slot.startsWith("iteration:")) {
            return "iteration_expression";
        }
        return "";
    }

    private String sourceTargetKind(String slot, Tree tree) {
        if (tree instanceof VariableTree) {
            if (slot.startsWith("local:")) {
                return "local_annotation";
            }
            if (slot.startsWith("field:")) {
                return "field_annotation";
            }
            if (slot.startsWith("parameter:")) {
                return "parameter_annotation";
            }
        }
        if (slot.equals("return") && tree instanceof MethodTree) {
            return "return_annotation";
        }
        return "";
    }

    private Iterable<AnnotationTree> annotationTrees(Tree tree) {
        java.util.List<AnnotationTree> result = new java.util.ArrayList<>();
        if (tree instanceof VariableTree) {
            result.addAll(((VariableTree) tree).getModifiers().getAnnotations());
        } else if (tree instanceof MethodTree) {
            MethodTree methodTree = (MethodTree) tree;
            result.addAll(methodTree.getModifiers().getAnnotations());
            collectAnnotationTrees(methodTree.getReturnType(), result);
        }
        return result;
    }

    private void collectAnnotationTrees(Tree tree, java.util.List<AnnotationTree> result) {
        if (tree == null) {
            return;
        }
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitAnnotation(AnnotationTree annotationTree, Void unused) {
                result.add(annotationTree);
                return super.visitAnnotation(annotationTree, unused);
            }
        }.scan(tree, null);
    }

    private void write(Map<String, Object> event) {
        try {
            writer.write(toJson(event));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new UserError("Could not write Nullness trace: %s", e.getMessage());
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return quote((String) value);
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        if (value instanceof String[]) {
            StringBuilder result = new StringBuilder("[");
            String[] values = (String[]) value;
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    result.append(',');
                }
                result.append(quote(values[i]));
            }
            return result.append(']').toString();
        }
        if (value instanceof Map<?, ?>) {
            StringBuilder result = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    result.append(',');
                }
                first = false;
                result.append(quote(entry.getKey().toString()));
                result.append(':');
                result.append(toJson(entry.getValue()));
            }
            return result.append('}').toString();
        }
        return quote(value.toString());
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

    @Override
    public void close() {
        if (!isEnabled()) {
            return;
        }
        try {
            writer.close();
        } catch (IOException e) {
            throw new UserError("Could not close Nullness trace: %s", e.getMessage());
        }
    }
}
