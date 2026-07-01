package checker_reconcile.trace;

/** V0 trace event names and required top-level fields. */
public final class TraceSchema {
    public static final String ASSUMPTION = "assumption";
    public static final String OBLIGATION = "obligation";
    public static final String DIAGNOSTIC = "diagnostic";
    public static final String FLOW_REFINEMENT = "flow_refinement";
    public static final String INFERENCE_DECISION = "inference_decision";

    private TraceSchema() {}

    public static void validateKnownEvent(TraceEvent event) {
        if (event.event.isEmpty()) {
            throw new IllegalArgumentException(
                    "trace line " + event.lineNumber + ": missing event");
        }
        if ((event.event.equals(ASSUMPTION)
                        || event.event.equals(OBLIGATION)
                        || event.event.equals(DIAGNOSTIC))
                && event.id.isEmpty()) {
            throw new IllegalArgumentException("trace line " + event.lineNumber + ": missing id");
        }
    }
}
