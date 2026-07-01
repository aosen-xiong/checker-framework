package checker_reconcile.diagnosis;

import java.util.List;

import checker_reconcile.constraints.TraceModel.DiagnosticSlice;
import checker_reconcile.repair.SuggestedRepair;

/** Ranks candidate repair sets for one diagnostic slice. */
public interface DiagnosisSolver {
    List<RepairCandidateSet> solve(
            ConstraintGraph graph,
            DiagnosticSlice slice,
            List<SuggestedRepair> repairs,
            SolverConfig config);
}
