package checker_reconcile.diagnosis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import checker_reconcile.constraints.TraceModel.DiagnosticSlice;
import checker_reconcile.trace.TraceEvent;

/** Extracts bounded correction sets from editable assumptions in one diagnostic slice. */
public final class CorrectionSetExtractor {
    public List<CorrectionSet> extract(DiagnosticSlice slice, SolverConfig config) {
        List<TraceEvent> assumptions = editableAssumptions(slice);
        List<CorrectionSet> result = new ArrayList<>();
        enumerate(assumptions, config.maxSetSize(), 0, new ArrayList<TraceEvent>(), result);
        result.sort(
                Comparator.comparingInt(
                                (CorrectionSet correctionSet) -> correctionSet.cost().value())
                        .thenComparingInt(correctionSet -> correctionSet.assumptionIds().size()));
        if (result.size() > config.maxCandidates()) {
            return new ArrayList<>(result.subList(0, config.maxCandidates()));
        }
        return result;
    }

    private List<TraceEvent> editableAssumptions(DiagnosticSlice slice) {
        List<TraceEvent> result = new ArrayList<>();
        for (TraceEvent assumption : slice.assumptions().values()) {
            if (Boolean.parseBoolean(assumption.stringField("editable"))) {
                result.add(assumption);
            }
        }
        result.sort(
                Comparator.comparingInt(this::weight).thenComparing(assumption -> assumption.id));
        return result;
    }

    private void enumerate(
            List<TraceEvent> assumptions,
            int maxSize,
            int start,
            List<TraceEvent> current,
            List<CorrectionSet> result) {
        if (!current.isEmpty()) {
            result.add(correctionSet(current));
        }
        if (current.size() == maxSize) {
            return;
        }
        for (int i = start; i < assumptions.size(); i++) {
            current.add(assumptions.get(i));
            enumerate(assumptions, maxSize, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    private CorrectionSet correctionSet(List<TraceEvent> assumptions) {
        List<String> ids = new ArrayList<>();
        int cost = 0;
        for (TraceEvent assumption : assumptions) {
            ids.add(assumption.id);
            cost += weight(assumption);
        }
        return new CorrectionSet(ids, new RepairCost(cost));
    }

    private int weight(TraceEvent event) {
        Object value = event.fields.get("weight");
        return value instanceof Number ? ((Number) value).intValue() : 1000;
    }
}
