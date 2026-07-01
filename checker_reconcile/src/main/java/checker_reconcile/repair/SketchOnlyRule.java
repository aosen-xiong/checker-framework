package checker_reconcile.repair;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import checker_reconcile.constraints.TraceModel.DiagnosticSlice;
import checker_reconcile.diagnosis.ConstraintEdge;
import checker_reconcile.diagnosis.ConstraintGraph;
import checker_reconcile.diagnosis.ConstraintNode;
import checker_reconcile.trace.TraceEvent;

/** Conservative non-automatic V0 repair sketches. */
public final class SketchOnlyRule implements RepairRule {
    @Override
    public List<SuggestedRepair> plan(Path source, DiagnosticSlice slice) throws IOException {
        return sketchesForAssumptions(slice, new ArrayList<>(slice.assumptions().values()));
    }

    @Override
    public List<SuggestedRepair> plan(
            Path source, DiagnosticSlice slice, Set<String> allowedAssumptions) throws IOException {
        return allowedAssumptions == null
                ? plan(source, slice)
                : Collections.<SuggestedRepair>emptyList();
    }

    @Override
    public List<SuggestedRepair> plan(
            Path source,
            DiagnosticSlice slice,
            Set<String> allowedAssumptions,
            ConstraintGraph graph)
            throws IOException {
        if (allowedAssumptions != null) {
            return Collections.emptyList();
        }
        return sketchesForAssumptions(slice, graphAssumptions(slice, graph));
    }

    private List<SuggestedRepair> sketchesForAssumptions(
            DiagnosticSlice slice, List<TraceEvent> assumptions) {
        List<SuggestedRepair> repairs = new ArrayList<>();
        repairs.add(
                new SuggestedRepair(
                        RepairKind.ADD_NULL_CHECK,
                        Collections.<SourceEdit>emptyList(),
                        RiskLevel.BODY_CHANGE,
                        false,
                        Collections.singletonList(slice.obligation().id),
                        "Sketch only in V0.",
                        nullCheckSketches(slice, assumptions)));
        repairs.add(
                new SuggestedRepair(
                        RepairKind.INTRODUCE_SUPPRESSION,
                        Collections.<SourceEdit>emptyList(),
                        RiskLevel.SUPPRESSION,
                        false,
                        Collections.singletonList(slice.diagnostic().id),
                        "Suppression is rejected by default."));
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

    private List<RepairSketch> nullCheckSketches(
            DiagnosticSlice slice, List<TraceEvent> assumptions) {
        List<RepairSketch> sketches = new ArrayList<>();
        for (TraceEvent assumption : assumptions) {
            RepairSketch sketch = nullCheckSketch(assumption);
            if (sketch != null) {
                sketches.add(sketch);
            }
        }
        if (sketches.isEmpty()) {
            sketches.add(
                    new RepairSketch(
                            "add_null_check",
                            slice.obligation().id,
                            false,
                            "Insert a null check before the failing use."));
        }
        return sketches;
    }

    @SuppressWarnings("unchecked")
    private RepairSketch nullCheckSketch(TraceEvent assumption) {
        Object sourceTargetValue = assumption.fields.get("source_target");
        if (sourceTargetValue instanceof Map<?, ?>) {
            return sourceTargetSketch(assumption, (Map<String, Object>) sourceTargetValue);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private RepairSketch sourceTargetSketch(
            TraceEvent assumption, Map<String, Object> sourceTarget) {
        Object expressionRangeValue = sourceTarget.get("expression_range");
        if (!(expressionRangeValue instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> expressionRange = (Map<String, Object>) expressionRangeValue;
        Object startOffsetValue = expressionRange.get("start_offset");
        Object endOffsetValue = expressionRange.get("end_offset");
        if (!(startOffsetValue instanceof Number) || !(endOffsetValue instanceof Number)) {
            return null;
        }
        return new RepairSketch(
                "add_null_check",
                assumption.id,
                false,
                "Insert a null check guarding " + sourceTarget.get("expression") + ".",
                stringValue(sourceTarget.get("kind")),
                stringValue(sourceTarget.get("expression")),
                ((Number) startOffsetValue).intValue(),
                ((Number) endOffsetValue).intValue(),
                sourceTargetAttributes(sourceTarget),
                "");
    }

    private Map<String, Object> sourceTargetAttributes(Map<String, Object> sourceTarget) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(sourceTarget);
        result.remove("kind");
        result.remove("expression");
        result.remove("expression_range");
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
