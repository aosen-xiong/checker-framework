package checker_reconcile.constraints;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import checker_reconcile.trace.TraceEvent;
import checker_reconcile.trace.TraceSchema;

/** Indexed trace model, sliced by diagnostic. */
public final class TraceModel {
    public final Map<String, TraceEvent> assumptions = new LinkedHashMap<>();
    public final Map<String, TraceEvent> obligations = new LinkedHashMap<>();
    public final Map<String, TraceEvent> diagnostics = new LinkedHashMap<>();

    public static TraceModel fromEvents(List<TraceEvent> events) {
        TraceModel model = new TraceModel();
        for (TraceEvent event : events) {
            TraceSchema.validateKnownEvent(event);
            switch (event.event) {
                case TraceSchema.ASSUMPTION:
                    model.assumptions.put(event.id, event);
                    break;
                case TraceSchema.OBLIGATION:
                    model.obligations.put(event.id, event);
                    break;
                case TraceSchema.DIAGNOSTIC:
                    model.diagnostics.put(event.id, event);
                    break;
                default:
                    break;
            }
        }
        return model;
    }

    public DiagnosticSlice slice(String diagnosticId) {
        TraceEvent diagnostic = diagnostics.get(diagnosticId);
        if (diagnostic == null) {
            throw new IllegalArgumentException("unknown diagnostic id: " + diagnosticId);
        }
        TraceEvent obligation = obligations.get(diagnostic.stringField("obligation"));
        if (obligation == null) {
            throw new IllegalArgumentException(
                    "diagnostic has no known obligation: " + diagnosticId);
        }
        Map<String, TraceEvent> deps = new LinkedHashMap<>();
        for (Object dep : obligation.listField("dependencies")) {
            TraceEvent assumption = assumptions.get(dep.toString());
            if (assumption != null) {
                deps.put(assumption.id, assumption);
            }
        }
        return new DiagnosticSlice(diagnostic, obligation, deps);
    }

    /** Events reachable from one diagnostic. */
    public static final class DiagnosticSlice {
        private final TraceEvent diagnostic;
        private final TraceEvent obligation;
        private final Map<String, TraceEvent> assumptions;

        public DiagnosticSlice(
                TraceEvent diagnostic, TraceEvent obligation, Map<String, TraceEvent> assumptions) {
            this.diagnostic = diagnostic;
            this.obligation = obligation;
            this.assumptions = assumptions;
        }

        public TraceEvent diagnostic() {
            return diagnostic;
        }

        public TraceEvent obligation() {
            return obligation;
        }

        public Map<String, TraceEvent> assumptions() {
            return assumptions;
        }
    }
}
