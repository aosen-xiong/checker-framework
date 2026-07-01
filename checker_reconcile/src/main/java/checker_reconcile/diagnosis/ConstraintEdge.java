package checker_reconcile.diagnosis;

/** A directed typed edge in the diagnostic constraint graph. */
public final class ConstraintEdge {
    private final String from;
    private final String to;
    private final EdgeKind kind;

    public ConstraintEdge(String from, String to, EdgeKind kind) {
        this.from = from;
        this.to = to;
        this.kind = kind;
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    public EdgeKind kind() {
        return kind;
    }

    /** Constraint graph edge kinds. */
    public enum EdgeKind {
        DIAGNOSTIC_OBLIGATION,
        OBLIGATION_ASSUMPTION,
        ASSUMPTION_SLOT,
        ASSUMPTION_QUALIFIER
    }
}
