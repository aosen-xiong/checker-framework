package checker_reconcile.diagnosis;

import checker_reconcile.trace.TraceEvent;

/** A typed node in the diagnostic constraint graph. */
public final class ConstraintNode {
    private final String id;
    private final NodeKind kind;
    private final TraceEvent event;

    public ConstraintNode(String id, NodeKind kind, TraceEvent event) {
        this.id = id;
        this.kind = kind;
        this.event = event;
    }

    public String id() {
        return id;
    }

    public NodeKind kind() {
        return kind;
    }

    public TraceEvent event() {
        return event;
    }

    /** Constraint graph node kinds. */
    public enum NodeKind {
        ASSUMPTION,
        OBLIGATION,
        DIAGNOSTIC,
        SLOT,
        QUALIFIER
    }
}
