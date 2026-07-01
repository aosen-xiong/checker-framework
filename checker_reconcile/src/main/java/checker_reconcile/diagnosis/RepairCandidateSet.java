package checker_reconcile.diagnosis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A ranked set of candidate repairs that may be applied together. */
public final class RepairCandidateSet {
    private final List<RepairCandidate> candidates;
    private final List<String> diagnosticIds;
    private final RepairCost cost;

    public RepairCandidateSet(List<RepairCandidate> candidates) {
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        Set<String> diagnostics = new LinkedHashSet<>();
        int total = 0;
        for (RepairCandidate candidate : candidates) {
            diagnostics.addAll(candidate.diagnosticIds());
            total += candidate.cost().value();
        }
        this.diagnosticIds = Collections.unmodifiableList(new ArrayList<>(diagnostics));
        this.cost = new RepairCost(total);
    }

    public List<RepairCandidate> candidates() {
        return candidates;
    }

    public List<String> diagnosticIds() {
        return diagnosticIds;
    }

    public RepairCost cost() {
        return cost;
    }
}
