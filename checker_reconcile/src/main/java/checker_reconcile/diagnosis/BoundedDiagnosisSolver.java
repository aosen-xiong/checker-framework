package checker_reconcile.diagnosis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import checker_reconcile.constraints.TraceModel.DiagnosticSlice;
import checker_reconcile.repair.RiskLevel;
import checker_reconcile.repair.SourceEdit;
import checker_reconcile.repair.SuggestedRepair;
import checker_reconcile.trace.TraceEvent;

/** Dependency-free bounded diagnosis solver over repair plans. */
public final class BoundedDiagnosisSolver implements DiagnosisSolver {
    @Override
    public List<RepairCandidateSet> solve(
            ConstraintGraph graph,
            DiagnosticSlice slice,
            List<SuggestedRepair> repairs,
            SolverConfig config) {
        List<RepairCandidate> candidates = new ArrayList<>();
        for (SuggestedRepair repair : repairs) {
            candidates.add(candidate(slice, repair));
        }
        candidates.sort(candidateComparator());

        List<RepairCandidateSet> sets = new ArrayList<>();
        enumerate(candidates, config.maxSetSize(), 0, new ArrayList<RepairCandidate>(), sets);
        sets.sort(setComparator());
        if (sets.size() > config.maxCandidates()) {
            return new ArrayList<>(sets.subList(0, config.maxCandidates()));
        }
        return sets;
    }

    private RepairCandidate candidate(DiagnosticSlice slice, SuggestedRepair repair) {
        List<String> assumptionIds = assumptionEvidence(slice, repair);
        int cost = 0;
        if (assumptionIds.isEmpty()) {
            cost += 100;
        }
        for (String assumptionId : assumptionIds) {
            TraceEvent assumption = slice.assumptions().get(assumptionId);
            cost += weight(assumption);
        }
        cost += RepairCost.riskPenalty(repair.risk());
        if (!repair.automatic()) {
            cost += 25;
        }
        if (repair.edits().isEmpty()) {
            cost += 10;
        }
        return new RepairCandidate(
                repair,
                assumptionIds,
                Collections.singletonList(slice.diagnostic().id),
                new RepairCost(cost));
    }

    private List<String> assumptionEvidence(DiagnosticSlice slice, SuggestedRepair repair) {
        Set<String> result = new LinkedHashSet<>();
        for (String evidenceId : repair.evidenceIds()) {
            if (slice.assumptions().containsKey(evidenceId)) {
                result.add(evidenceId);
            }
        }
        return new ArrayList<>(result);
    }

    private int weight(TraceEvent event) {
        if (event == null) {
            return 1000;
        }
        Object value = event.fields.get("weight");
        return value instanceof Number ? ((Number) value).intValue() : 1000;
    }

    private void enumerate(
            List<RepairCandidate> candidates,
            int maxSize,
            int start,
            List<RepairCandidate> current,
            List<RepairCandidateSet> result) {
        if (!current.isEmpty()) {
            result.add(new RepairCandidateSet(current));
        }
        if (current.size() == maxSize) {
            return;
        }
        for (int i = start; i < candidates.size(); i++) {
            RepairCandidate candidate = candidates.get(i);
            if (!compatible(current, candidate)) {
                continue;
            }
            current.add(candidate);
            enumerate(candidates, maxSize, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    private boolean compatible(List<RepairCandidate> current, RepairCandidate candidate) {
        for (RepairCandidate existing : current) {
            if (sharesAssumption(existing, candidate)
                    || overlaps(existing.repair().edits(), candidate.repair().edits())) {
                return false;
            }
        }
        return !mixesSuppressionAndConcreteEdit(current, candidate);
    }

    private boolean sharesAssumption(RepairCandidate first, RepairCandidate second) {
        for (String assumptionId : first.assumptionIds()) {
            if (second.assumptionIds().contains(assumptionId)) {
                return true;
            }
        }
        return false;
    }

    private boolean overlaps(List<SourceEdit> first, List<SourceEdit> second) {
        for (SourceEdit firstEdit : first) {
            for (SourceEdit secondEdit : second) {
                if (firstEdit.file().equals(secondEdit.file())
                        && firstEdit.startOffset() < secondEdit.endOffset()
                        && secondEdit.startOffset() < firstEdit.endOffset()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean mixesSuppressionAndConcreteEdit(
            List<RepairCandidate> current, RepairCandidate candidate) {
        boolean hasSuppression = candidate.risk() == RiskLevel.SUPPRESSION;
        boolean hasConcreteEdit = !candidate.repair().edits().isEmpty();
        for (RepairCandidate existing : current) {
            hasSuppression = hasSuppression || existing.risk() == RiskLevel.SUPPRESSION;
            hasConcreteEdit = hasConcreteEdit || !existing.repair().edits().isEmpty();
        }
        return hasSuppression && hasConcreteEdit;
    }

    private Comparator<RepairCandidate> candidateComparator() {
        return Comparator.comparingInt((RepairCandidate candidate) -> candidate.cost().value())
                .thenComparing(candidate -> candidate.risk().name())
                .thenComparing(candidate -> candidate.kind().name())
                .thenComparing(RepairCandidate::message);
    }

    private Comparator<RepairCandidateSet> setComparator() {
        return Comparator.comparingInt((RepairCandidateSet set) -> set.cost().value())
                .thenComparingInt(set -> set.candidates().size());
    }
}
