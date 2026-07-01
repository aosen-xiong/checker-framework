package checker_reconcile.diagnosis;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import checker_reconcile.constraints.TraceModel;
import checker_reconcile.constraints.TraceModel.DiagnosticSlice;
import checker_reconcile.repair.NullCheckEditPlanner;
import checker_reconcile.repair.RepairKind;
import checker_reconcile.repair.RepairPlanner;
import checker_reconcile.repair.SuggestedRepair;

/** Builds graph-backed ranked repair candidates for diagnostics. */
public final class DiagnosisEngine {
    private final RepairPlanner planner;
    private final DiagnosisSolver solver;
    private final SolverConfig config;
    private final CorrectionSetExtractor correctionSetExtractor;
    private final CandidateSetReducer candidateSetReducer;
    private final boolean includeSketchEdits;

    public DiagnosisEngine() {
        this(
                new RepairPlanner(),
                new BoundedDiagnosisSolver(),
                SolverConfig.defaults(),
                new CorrectionSetExtractor(),
                new CandidateSetReducer(),
                false);
    }

    public DiagnosisEngine(RepairPlanner planner, DiagnosisSolver solver, SolverConfig config) {
        this(planner, solver, config, new CorrectionSetExtractor(), new CandidateSetReducer());
    }

    public DiagnosisEngine(
            RepairPlanner planner,
            DiagnosisSolver solver,
            SolverConfig config,
            CorrectionSetExtractor correctionSetExtractor,
            CandidateSetReducer candidateSetReducer) {
        this(planner, solver, config, correctionSetExtractor, candidateSetReducer, false);
    }

    public DiagnosisEngine(boolean includeSketchEdits) {
        this(
                new RepairPlanner(),
                new BoundedDiagnosisSolver(),
                SolverConfig.defaults(),
                new CorrectionSetExtractor(),
                new CandidateSetReducer(),
                includeSketchEdits);
    }

    public DiagnosisEngine(
            RepairPlanner planner,
            DiagnosisSolver solver,
            SolverConfig config,
            CorrectionSetExtractor correctionSetExtractor,
            CandidateSetReducer candidateSetReducer,
            boolean includeSketchEdits) {
        this.planner = planner;
        this.solver = solver;
        this.config = config;
        this.correctionSetExtractor = correctionSetExtractor;
        this.candidateSetReducer = candidateSetReducer;
        this.includeSketchEdits = includeSketchEdits;
    }

    public List<RepairCandidateSet> diagnose(Path source, TraceModel model, DiagnosticSlice slice)
            throws IOException {
        ConstraintGraph graph = ConstraintGraph.fromModel(model);
        List<SuggestedRepair> repairs = new ArrayList<>();
        for (CorrectionSet correctionSet : correctionSetExtractor.extract(slice, config)) {
            repairs.addAll(planner.plan(source, slice, correctionSet, graph));
        }
        if (includeSketchEdits) {
            repairs.addAll(materializedSketches(source, slice, graph));
        } else if (repairs.isEmpty()) {
            repairs.addAll(materializedNullCheckSketches(source, slice, graph));
        }
        return candidateSetReducer.reduce(solver.solve(graph, slice, repairs, config));
    }

    private List<SuggestedRepair> materializedSketches(
            Path source, DiagnosticSlice slice, ConstraintGraph graph) throws IOException {
        return new NullCheckEditPlanner()
                .addNullCheckEdits(
                        source, planner.plan(source, slice, (java.util.Set<String>) null, graph));
    }

    private List<SuggestedRepair> materializedNullCheckSketches(
            Path source, DiagnosticSlice slice, ConstraintGraph graph) throws IOException {
        List<SuggestedRepair> repairs = new ArrayList<>();
        for (SuggestedRepair repair : materializedSketches(source, slice, graph)) {
            if (repair.kind() == RepairKind.ADD_NULL_CHECK && hasSourceExpression(repair)) {
                repairs.add(repair);
            }
        }
        return repairs;
    }

    private boolean hasSourceExpression(SuggestedRepair repair) {
        if (!repair.edits().isEmpty()) {
            return true;
        }
        for (checker_reconcile.repair.RepairSketch sketch : repair.sketches()) {
            if (sketch.startOffset() != null && sketch.endOffset() != null) {
                return true;
            }
        }
        return false;
    }
}
