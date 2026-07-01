package checker_reconcile.repair;

/** A repair plan read from JSONL with its diagnostic id. */
public final class PlannedRepair {
    private final String diagnosticId;
    private final SuggestedRepair repair;
    private final String origin;
    private final Double confidence;
    private final boolean requiresValidation;

    public PlannedRepair(String diagnosticId, SuggestedRepair repair) {
        this(diagnosticId, repair, "deterministic", null, false);
    }

    public PlannedRepair(
            String diagnosticId,
            SuggestedRepair repair,
            String origin,
            Double confidence,
            boolean requiresValidation) {
        this.diagnosticId = diagnosticId;
        this.repair = repair;
        this.origin = origin;
        this.confidence = confidence;
        this.requiresValidation = requiresValidation;
    }

    public String diagnosticId() {
        return diagnosticId;
    }

    public SuggestedRepair repair() {
        return repair;
    }

    public String origin() {
        return origin;
    }

    public Double confidence() {
        return confidence;
    }

    public boolean requiresValidation() {
        return requiresValidation;
    }
}
