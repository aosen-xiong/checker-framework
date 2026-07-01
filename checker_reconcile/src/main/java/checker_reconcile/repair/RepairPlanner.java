package checker_reconcile.repair;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import checker_reconcile.constraints.TraceModel.DiagnosticSlice;
import checker_reconcile.diagnosis.ConstraintGraph;
import checker_reconcile.diagnosis.CorrectionSet;

/** Converts diagnostic slices into typed repair plans. */
public final class RepairPlanner {
    private final List<RepairRule> rules;
    private final AgentRepairAdvisor agentRepairAdvisor;

    public RepairPlanner() {
        this(new NoopAgentRepairAdvisor());
    }

    public RepairPlanner(AgentRepairAdvisor agentRepairAdvisor) {
        this(defaultRules(), agentRepairAdvisor);
    }

    public RepairPlanner(List<RepairRule> rules, AgentRepairAdvisor agentRepairAdvisor) {
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
        this.agentRepairAdvisor = agentRepairAdvisor;
    }

    public List<SuggestedRepair> plan(Path source, DiagnosticSlice slice) throws IOException {
        return plan(source, slice, (Set<String>) null);
    }

    public List<SuggestedRepair> plan(
            Path source, DiagnosticSlice slice, CorrectionSet correctionSet) throws IOException {
        return plan(source, slice, new LinkedHashSet<>(correctionSet.assumptionIds()));
    }

    public List<SuggestedRepair> plan(
            Path source, DiagnosticSlice slice, CorrectionSet correctionSet, ConstraintGraph graph)
            throws IOException {
        return plan(source, slice, new LinkedHashSet<>(correctionSet.assumptionIds()), graph);
    }

    public List<SuggestedRepair> plan(
            Path source, DiagnosticSlice slice, Set<String> allowedAssumptions) throws IOException {
        return plan(source, slice, allowedAssumptions, null);
    }

    public List<SuggestedRepair> plan(
            Path source,
            DiagnosticSlice slice,
            Set<String> allowedAssumptions,
            ConstraintGraph graph)
            throws IOException {
        List<SuggestedRepair> repairs = new ArrayList<>();
        for (RepairRule rule : rules) {
            repairs.addAll(rule.plan(source, slice, allowedAssumptions, graph));
        }
        repairs.addAll(agentRepairAdvisor.advise(new AgentRepairRequest(source, slice, repairs)));
        return repairs;
    }

    private static List<RepairRule> defaultRules() {
        return Arrays.<RepairRule>asList(
                new ExplicitQualifierWeakeningRule(), new SketchOnlyRule());
    }
}
