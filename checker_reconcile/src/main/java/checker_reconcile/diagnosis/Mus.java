package checker_reconcile.diagnosis;

import java.util.ArrayList;
import java.util.List;

import checker_reconcile.constraints.TraceModel.DiagnosticSlice;

/** Deletion-style MUS over a single diagnostic slice. */
public final class Mus {
    public List<String> compute(DiagnosticSlice slice) {
        List<String> result = new ArrayList<>();
        result.addAll(slice.assumptions().keySet());
        result.add(slice.obligation().id);
        return result;
    }
}
