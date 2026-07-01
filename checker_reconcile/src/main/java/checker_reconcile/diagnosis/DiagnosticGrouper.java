package checker_reconcile.diagnosis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import checker_reconcile.constraints.TraceModel;
import checker_reconcile.trace.TraceEvent;

/** Groups diagnostics by shared assumptions, slots, and source targets. */
public final class DiagnosticGrouper {
    public List<DiagnosticGroup> group(TraceModel model) {
        Map<String, Set<String>> keysByDiagnostic = new LinkedHashMap<>();
        for (String diagnosticId : model.diagnostics.keySet()) {
            keysByDiagnostic.put(diagnosticId, keys(model.slice(diagnosticId)));
        }

        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (String diagnosticId : keysByDiagnostic.keySet()) {
            graph.put(diagnosticId, new LinkedHashSet<String>());
        }
        List<String> diagnosticIds = new ArrayList<>(keysByDiagnostic.keySet());
        for (int i = 0; i < diagnosticIds.size(); i++) {
            for (int j = i + 1; j < diagnosticIds.size(); j++) {
                String first = diagnosticIds.get(i);
                String second = diagnosticIds.get(j);
                if (intersects(keysByDiagnostic.get(first), keysByDiagnostic.get(second))) {
                    graph.get(first).add(second);
                    graph.get(second).add(first);
                }
            }
        }

        List<DiagnosticGroup> groups = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String diagnosticId : diagnosticIds) {
            if (visited.contains(diagnosticId)) {
                continue;
            }
            List<String> component = new ArrayList<>();
            Set<String> componentKeys = new LinkedHashSet<>();
            Deque<String> worklist = new ArrayDeque<>();
            worklist.add(diagnosticId);
            visited.add(diagnosticId);
            while (!worklist.isEmpty()) {
                String current = worklist.removeFirst();
                component.add(current);
                componentKeys.addAll(keysByDiagnostic.get(current));
                for (String next : graph.get(current)) {
                    if (visited.add(next)) {
                        worklist.addLast(next);
                    }
                }
            }
            Collections.sort(component);
            groups.add(new DiagnosticGroup(component, componentKeys));
        }
        return groups;
    }

    private Set<String> keys(TraceModel.DiagnosticSlice slice) {
        Set<String> result = new LinkedHashSet<>();
        for (TraceEvent assumption : slice.assumptions().values()) {
            result.add("assumption:" + assumption.id);
            String slot = assumption.stringField("slot");
            if (!slot.isEmpty() && !slot.startsWith("expr:")) {
                result.add("slot:" + slot);
            }
            String sourceTargetKey = sourceTargetKey(assumption);
            if (!sourceTargetKey.isEmpty()) {
                result.add(sourceTargetKey);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String sourceTargetKey(TraceEvent assumption) {
        Object sourceTargetValue = assumption.fields.get("source_target");
        if (!(sourceTargetValue instanceof Map<?, ?>)) {
            return "";
        }
        Map<String, Object> sourceTarget = (Map<String, Object>) sourceTargetValue;
        Object rangeValue = sourceTarget.get("annotation_range");
        if (!(rangeValue instanceof Map<?, ?>)) {
            return "";
        }
        Map<String, Object> range = (Map<String, Object>) rangeValue;
        Object start = range.get("start_offset");
        Object end = range.get("end_offset");
        if (!(start instanceof Number) || !(end instanceof Number)) {
            return "";
        }
        return "source_target:"
                + sourceTarget.get("kind")
                + ":"
                + ((Number) start).intValue()
                + "-"
                + ((Number) end).intValue();
    }

    private boolean intersects(Set<String> first, Set<String> second) {
        for (String value : first) {
            if (second.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
