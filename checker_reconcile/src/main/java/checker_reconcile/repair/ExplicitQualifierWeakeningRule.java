package checker_reconcile.repair;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import checker_reconcile.constraints.Nullness;
import checker_reconcile.constraints.TraceModel.DiagnosticSlice;
import checker_reconcile.diagnosis.ConstraintEdge;
import checker_reconcile.diagnosis.ConstraintGraph;
import checker_reconcile.diagnosis.ConstraintNode;
import checker_reconcile.trace.TraceEvent;

/** Plans qualifier weakening repairs derived from failed diagnostic constraints. */
public final class ExplicitQualifierWeakeningRule implements RepairRule {
    private final SourceTargetResolver sourceTargetResolver = new SourceTargetResolver();

    @Override
    public List<SuggestedRepair> plan(Path source, DiagnosticSlice slice) throws IOException {
        return plan(source, slice, null);
    }

    @Override
    public List<SuggestedRepair> plan(
            Path source, DiagnosticSlice slice, Set<String> allowedAssumptions) throws IOException {
        return plan(source, slice, allowedAssumptions, null);
    }

    @Override
    public List<SuggestedRepair> plan(
            Path source,
            DiagnosticSlice slice,
            Set<String> allowedAssumptions,
            ConstraintGraph graph)
            throws IOException {
        List<SuggestedRepair> repairs = new ArrayList<>();
        for (TraceEvent assumption : graphAssumptions(slice, graph)) {
            if (allowedAssumptions != null && !allowedAssumptions.contains(assumption.id)) {
                continue;
            }
            SuggestedRepair repair = repairForAssumption(source, slice, assumption);
            if (repair != null) {
                repairs.add(repair);
            }
        }
        return repairs;
    }

    private List<TraceEvent> graphAssumptions(DiagnosticSlice slice, ConstraintGraph graph) {
        if (graph == null) {
            return new ArrayList<>(slice.assumptions().values());
        }
        List<TraceEvent> result = new ArrayList<>();
        for (ConstraintEdge edge : graph.edges()) {
            if (edge.kind() != ConstraintEdge.EdgeKind.OBLIGATION_ASSUMPTION
                    || !edge.from().equals(slice.obligation().id)) {
                continue;
            }
            ConstraintNode node = graph.nodes().get(edge.to());
            if (node != null && node.event() != null) {
                result.add(node.event());
            }
        }
        return result;
    }

    private SuggestedRepair repairForAssumption(
            Path source, DiagnosticSlice slice, TraceEvent assumption) throws IOException {
        if (!isCandidate(assumption)) {
            return null;
        }
        String fromQualifier = editableQualifier(assumption);
        String toQualifier = replacementQualifier(slice, fromQualifier);
        if (toQualifier.isEmpty() || !Nullness.isWeakening(fromQualifier, toQualifier)) {
            return null;
        }
        SourceTarget explicitTarget =
                sourceTargetResolver.resolveExplicitAnnotationTarget(
                        source, assumption, fromQualifier);
        if (explicitTarget != null) {
            return repair(slice, assumption, explicitTarget, fromQualifier, toQualifier, true);
        }
        SourceTarget inferredTarget =
                sourceTargetResolver.resolveAnnotationTarget(source, assumption, fromQualifier);
        if (inferredTarget != null) {
            return repair(slice, assumption, inferredTarget, fromQualifier, toQualifier, false);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String editableQualifier(TraceEvent assumption) {
        Object sourceTargetValue = assumption.fields.get("source_target");
        if (sourceTargetValue instanceof Map<?, ?>) {
            Object annotation = ((Map<String, Object>) sourceTargetValue).get("annotation");
            if (annotation != null) {
                return annotation.toString();
            }
        }
        return Nullness.qualifierOf(assumption.stringField("type"));
    }

    private String replacementQualifier(DiagnosticSlice slice, String fromQualifier) {
        List<String> gotQualifiers = Nullness.qualifiersOf(slice.obligation().stringField("got"));
        List<String> wantQualifiers = Nullness.qualifiersOf(slice.obligation().stringField("want"));
        int qualifierCount = Math.min(gotQualifiers.size(), wantQualifiers.size());
        for (int i = 0; i < qualifierCount; i++) {
            String gotQualifier = gotQualifiers.get(i);
            String wantQualifier = wantQualifiers.get(i);
            if (fromQualifier.equals(wantQualifier)
                    && !fromQualifier.equals(gotQualifier)
                    && Nullness.isWeakening(fromQualifier, gotQualifier)) {
                return gotQualifier;
            }
        }

        String gotQualifier = Nullness.qualifierOf(slice.obligation().stringField("got"));
        String wantQualifier = Nullness.qualifierOf(slice.obligation().stringField("want"));
        if (fromQualifier.equals(wantQualifier)
                && !fromQualifier.equals(gotQualifier)
                && Nullness.isWeakening(fromQualifier, gotQualifier)) {
            return gotQualifier;
        }
        return "";
    }

    private SuggestedRepair repair(
            DiagnosticSlice slice,
            TraceEvent assumption,
            SourceTarget target,
            String fromQualifier,
            String toQualifier,
            boolean automatic) {
        RiskLevel risk = target.kind().risk();
        if (risk == RiskLevel.UNKNOWN) {
            return null;
        }
        return new SuggestedRepair(
                RepairKind.CHANGE_QUALIFIER,
                Collections.singletonList(target.replaceWith(toQualifier)),
                risk,
                automatic,
                Arrays.asList(assumption.id, slice.obligation().id, slice.diagnostic().id),
                "Change "
                        + (automatic ? "explicit " : "inferred ")
                        + target.syntacticKind()
                        + " from "
                        + fromQualifier
                        + " to "
                        + toQualifier
                        + ".");
    }

    private boolean isCandidate(TraceEvent assumption) {
        boolean editable = Boolean.parseBoolean(assumption.stringField("editable"));
        return editable && !assumption.stringField("type").isEmpty();
    }
}
