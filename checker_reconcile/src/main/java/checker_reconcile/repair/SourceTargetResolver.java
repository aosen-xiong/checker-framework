package checker_reconcile.repair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import checker_reconcile.trace.TraceEvent;

/** Resolves editable trace assumptions to source targets. */
public final class SourceTargetResolver {
    public SourceTarget resolveAnnotationTarget(
            Path source, TraceEvent assumption, String annotation) throws IOException {
        SourceTarget explicitTarget =
                resolveExplicitAnnotationTarget(source, assumption, annotation);
        if (explicitTarget != null) {
            return explicitTarget;
        }
        if (!canInferLocalAnnotationTarget(assumption)) {
            return null;
        }

        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        SourceTarget rangeTarget = rangeTarget(source, text, assumption, annotation);
        if (rangeTarget != null) {
            return rangeTarget;
        }
        return lineTarget(source, text, assumption, annotation);
    }

    private boolean canInferLocalAnnotationTarget(TraceEvent assumption) {
        String slot = assumption.stringField("slot");
        return !slot.startsWith("field:")
                && !slot.startsWith("parameter:")
                && !slot.equals("return");
    }

    public SourceTarget resolveExplicitAnnotationTarget(
            Path source, TraceEvent assumption, String annotation) {
        SourceTarget explicitTarget = explicitSourceTarget(source, assumption, annotation);
        if (explicitTarget != null) {
            return explicitTarget;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private SourceTarget explicitSourceTarget(
            Path source, TraceEvent assumption, String annotation) {
        Object sourceTargetValue = assumption.fields.get("source_target");
        if (!(sourceTargetValue instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> sourceTarget = (Map<String, Object>) sourceTargetValue;
        if (!annotation.equals(sourceTarget.get("annotation"))) {
            return null;
        }
        Object annotationRangeValue = sourceTarget.get("annotation_range");
        if (!(annotationRangeValue instanceof Map<?, ?>)) {
            return null;
        }
        SourceTarget target =
                targetFromOffsetRange(
                        source,
                        (Map<String, Object>) annotationRangeValue,
                        stringValue(sourceTarget.get("kind"), "annotation"));
        if (target == null) {
            return null;
        }
        return target;
    }

    @SuppressWarnings("unchecked")
    private SourceTarget rangeTarget(
            Path source, String text, TraceEvent assumption, String annotation) {
        Object rangeValue = assumption.fields.get("range");
        if (!(rangeValue instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> range = (Map<String, Object>) rangeValue;
        SourceTarget declarationTarget = targetFromOffsetRange(source, range, "local_annotation");
        if (declarationTarget == null || declarationTarget.endOffset() > text.length()) {
            return null;
        }
        int annotationStart =
                text.substring(declarationTarget.startOffset(), declarationTarget.endOffset())
                        .indexOf(annotation);
        if (annotationStart < 0) {
            return null;
        }
        int startOffset = declarationTarget.startOffset() + annotationStart;
        return new SourceTarget(
                source,
                startOffset,
                startOffset + annotation.length(),
                SourceTargetKind.LOCAL_ANNOTATION);
    }

    @SuppressWarnings("unchecked")
    private SourceTarget lineTarget(
            Path source, String text, TraceEvent assumption, String annotation) {
        Object rangeValue = assumption.fields.get("range");
        if (!(rangeValue instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> range = (Map<String, Object>) rangeValue;
        Object startLineValue = range.get("start_line");
        if (!(startLineValue instanceof Number)) {
            return null;
        }

        int lineStart = lineStartOffset(text, ((Number) startLineValue).intValue());
        if (lineStart < 0) {
            return null;
        }
        int lineEnd = text.indexOf('\n', lineStart);
        if (lineEnd < 0) {
            lineEnd = text.length();
        }
        String line = text.substring(lineStart, lineEnd);
        int annotationIndex = line.indexOf(annotation);
        if (annotationIndex < 0) {
            return null;
        }
        int startOffset = lineStart + annotationIndex;
        return new SourceTarget(
                source,
                startOffset,
                startOffset + annotation.length(),
                SourceTargetKind.LOCAL_ANNOTATION);
    }

    private SourceTarget targetFromOffsetRange(
            Path source, Map<String, Object> range, String syntacticKind) {
        Object startOffsetValue = range.get("start_offset");
        Object endOffsetValue = range.get("end_offset");
        if (!(startOffsetValue instanceof Number) || !(endOffsetValue instanceof Number)) {
            return null;
        }
        int startOffset = ((Number) startOffsetValue).intValue();
        int endOffset = ((Number) endOffsetValue).intValue();
        if (startOffset < 0 || endOffset < startOffset) {
            return null;
        }
        return new SourceTarget(
                source, startOffset, endOffset, SourceTargetKind.fromWireName(syntacticKind));
    }

    private String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : value.toString();
    }

    private int lineStartOffset(String text, int oneBasedLine) {
        if (oneBasedLine < 1) {
            return -1;
        }
        int line = 1;
        int offset = 0;
        while (line < oneBasedLine) {
            int next = text.indexOf('\n', offset);
            if (next < 0) {
                return -1;
            }
            offset = next + 1;
            line++;
        }
        return offset;
    }
}
