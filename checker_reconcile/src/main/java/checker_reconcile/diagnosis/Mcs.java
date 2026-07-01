package checker_reconcile.diagnosis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import checker_reconcile.constraints.TraceModel.DiagnosticSlice;
import checker_reconcile.trace.TraceEvent;

/** Editable-assumption MCS candidates, ordered by trace weight. */
public final class Mcs {
    public List<String> compute(DiagnosticSlice slice) {
        List<TraceEvent> editable = new ArrayList<>();
        for (TraceEvent event : slice.assumptions().values()) {
            if (Boolean.parseBoolean(event.stringField("editable"))) {
                editable.add(event);
            }
        }
        editable.sort(Comparator.comparingInt(this::weight));
        List<String> result = new ArrayList<>();
        for (TraceEvent event : editable) {
            result.add(event.id);
        }
        return result;
    }

    private int weight(TraceEvent event) {
        Object value = event.fields.get("weight");
        return value instanceof Number ? ((Number) value).intValue() : 1000;
    }
}
