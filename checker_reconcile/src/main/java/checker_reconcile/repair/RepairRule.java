package checker_reconcile.repair;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import checker_reconcile.constraints.TraceModel.DiagnosticSlice;
import checker_reconcile.diagnosis.ConstraintGraph;

/** Deterministic repair rule over one diagnostic slice. */
public interface RepairRule {
    List<SuggestedRepair> plan(Path source, DiagnosticSlice slice) throws IOException;

    default List<SuggestedRepair> plan(
            Path source, DiagnosticSlice slice, Set<String> allowedAssumptions) throws IOException {
        return plan(source, slice);
    }

    default List<SuggestedRepair> plan(
            Path source,
            DiagnosticSlice slice,
            Set<String> allowedAssumptions,
            ConstraintGraph graph)
            throws IOException {
        return plan(source, slice, allowedAssumptions);
    }
}
