package checker_reconcile.diagnosis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import checker_reconcile.constraints.TraceModel;
import checker_reconcile.diagnosis.ConstraintEdge.EdgeKind;
import checker_reconcile.diagnosis.ConstraintNode.NodeKind;
import checker_reconcile.trace.TraceEvent;

/** Typed graph view over trace assumptions, obligations, diagnostics, slots, and qualifiers. */
public final class ConstraintGraph {
    private final Map<String, ConstraintNode> nodes;
    private final List<ConstraintEdge> edges;

    private ConstraintGraph(Map<String, ConstraintNode> nodes, List<ConstraintEdge> edges) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
    }

    public static ConstraintGraph fromModel(TraceModel model) {
        Map<String, ConstraintNode> nodes = new LinkedHashMap<>();
        List<ConstraintEdge> edges = new ArrayList<>();
        for (TraceEvent assumption : model.assumptions.values()) {
            addEventNode(nodes, assumption, NodeKind.ASSUMPTION);
            String slot = assumption.stringField("slot");
            if (!slot.isEmpty()) {
                String slotId = "slot:" + slot;
                nodes.putIfAbsent(slotId, new ConstraintNode(slotId, NodeKind.SLOT, null));
                edges.add(new ConstraintEdge(assumption.id, slotId, EdgeKind.ASSUMPTION_SLOT));
            }
            String qualifier = assumption.stringField("type");
            if (!qualifier.isEmpty()) {
                String qualifierId = "qualifier:" + qualifier;
                nodes.putIfAbsent(
                        qualifierId, new ConstraintNode(qualifierId, NodeKind.QUALIFIER, null));
                edges.add(
                        new ConstraintEdge(
                                assumption.id, qualifierId, EdgeKind.ASSUMPTION_QUALIFIER));
            }
        }
        for (TraceEvent obligation : model.obligations.values()) {
            addEventNode(nodes, obligation, NodeKind.OBLIGATION);
            for (Object dependency : obligation.listField("dependencies")) {
                String assumptionId = dependency.toString();
                if (nodes.containsKey(assumptionId)) {
                    edges.add(
                            new ConstraintEdge(
                                    obligation.id, assumptionId, EdgeKind.OBLIGATION_ASSUMPTION));
                }
            }
        }
        for (TraceEvent diagnostic : model.diagnostics.values()) {
            addEventNode(nodes, diagnostic, NodeKind.DIAGNOSTIC);
            String obligationId = diagnostic.stringField("obligation");
            if (nodes.containsKey(obligationId)) {
                edges.add(
                        new ConstraintEdge(
                                diagnostic.id, obligationId, EdgeKind.DIAGNOSTIC_OBLIGATION));
            }
        }
        return new ConstraintGraph(nodes, edges);
    }

    private static void addEventNode(
            Map<String, ConstraintNode> nodes, TraceEvent event, NodeKind kind) {
        nodes.putIfAbsent(event.id, new ConstraintNode(event.id, kind, event));
    }

    public Map<String, ConstraintNode> nodes() {
        return nodes;
    }

    public List<ConstraintEdge> edges() {
        return edges;
    }
}
