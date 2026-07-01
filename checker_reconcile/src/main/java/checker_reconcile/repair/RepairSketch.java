package checker_reconcile.repair;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A proposed source repair. Automatic V0 patches are intentionally conservative. */
public final class RepairSketch {
    private final String kind;
    private final String targetId;
    private final boolean automatic;
    private final String message;
    private final String sourceTargetKind;
    private final String expression;
    private final Integer startOffset;
    private final Integer endOffset;
    private final Map<String, Object> sourceTargetAttributes;
    private final String materializationFailure;

    public RepairSketch(String kind, String targetId, boolean automatic, String message) {
        this(kind, targetId, automatic, message, "", "", null, null, "");
    }

    public RepairSketch(
            String kind,
            String targetId,
            boolean automatic,
            String message,
            String sourceTargetKind,
            String expression,
            Integer startOffset,
            Integer endOffset) {
        this(
                kind,
                targetId,
                automatic,
                message,
                sourceTargetKind,
                expression,
                startOffset,
                endOffset,
                Collections.emptyMap(),
                "");
    }

    public RepairSketch(
            String kind,
            String targetId,
            boolean automatic,
            String message,
            String sourceTargetKind,
            String expression,
            Integer startOffset,
            Integer endOffset,
            String materializationFailure) {
        this(
                kind,
                targetId,
                automatic,
                message,
                sourceTargetKind,
                expression,
                startOffset,
                endOffset,
                Collections.emptyMap(),
                materializationFailure);
    }

    public RepairSketch(
            String kind,
            String targetId,
            boolean automatic,
            String message,
            String sourceTargetKind,
            String expression,
            Integer startOffset,
            Integer endOffset,
            Map<String, Object> sourceTargetAttributes,
            String materializationFailure) {
        this.kind = kind;
        this.targetId = targetId;
        this.automatic = automatic;
        this.message = message;
        this.sourceTargetKind = sourceTargetKind;
        this.expression = expression;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.sourceTargetAttributes =
                Collections.unmodifiableMap(new LinkedHashMap<>(sourceTargetAttributes));
        this.materializationFailure = materializationFailure;
    }

    public String kind() {
        return kind;
    }

    public String targetId() {
        return targetId;
    }

    public boolean automatic() {
        return automatic;
    }

    public String message() {
        return message;
    }

    public String sourceTargetKind() {
        return sourceTargetKind;
    }

    public String expression() {
        return expression;
    }

    public Integer startOffset() {
        return startOffset;
    }

    public Integer endOffset() {
        return endOffset;
    }

    public Map<String, Object> sourceTargetAttributes() {
        return sourceTargetAttributes;
    }

    public boolean booleanSourceTargetAttribute(String name) {
        Object value = sourceTargetAttributes.get(name);
        return value instanceof Boolean && (Boolean) value;
    }

    public boolean hasSourceTargetAttribute(String name) {
        return sourceTargetAttributes.containsKey(name);
    }

    public String stringSourceTargetAttribute(String name) {
        Object value = sourceTargetAttributes.get(name);
        return value == null ? "" : value.toString();
    }

    public String materializationFailure() {
        return materializationFailure;
    }

    public RepairSketch withMaterializationFailure(String reason) {
        return new RepairSketch(
                kind,
                targetId,
                automatic,
                message,
                sourceTargetKind,
                expression,
                startOffset,
                endOffset,
                sourceTargetAttributes,
                reason);
    }
}
