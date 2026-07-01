package checker_reconcile.diagnosis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import checker_reconcile.repair.SourceEdit;

/** Reduces redundant candidate sets while preserving new repair insight. */
public final class CandidateSetReducer {
    public List<RepairCandidateSet> reduce(List<RepairCandidateSet> candidateSets) {
        List<RepairCandidateSet> ordered = new ArrayList<>(candidateSets);
        ordered.sort(
                Comparator.comparingInt((RepairCandidateSet set) -> set.cost().value())
                        .thenComparingInt(set -> set.candidates().size())
                        .thenComparing(this::insightKey));

        Map<String, RepairCandidateSet> cheapestByInsight = new LinkedHashMap<>();
        for (RepairCandidateSet candidateSet : ordered) {
            String key = insightKey(candidateSet);
            if (!cheapestByInsight.containsKey(key)) {
                cheapestByInsight.put(key, candidateSet);
            }
        }

        List<RepairCandidateSet> result = new ArrayList<>();
        Set<String> covered = new LinkedHashSet<>();
        for (RepairCandidateSet candidateSet : cheapestByInsight.values()) {
            if (isMultiRepairEmptyEditSet(candidateSet)) {
                continue;
            }
            Set<String> coverage = coverage(candidateSet);
            if (coverage.isEmpty() || addsCoverage(coverage, covered)) {
                result.add(candidateSet);
                covered.addAll(coverage);
            }
        }
        return result;
    }

    private boolean addsCoverage(Set<String> coverage, Set<String> covered) {
        for (String key : coverage) {
            if (!covered.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMultiRepairEmptyEditSet(RepairCandidateSet candidateSet) {
        if (candidateSet.candidates().size() <= 1) {
            return false;
        }
        for (RepairCandidate candidate : candidateSet.candidates()) {
            if (!candidate.repair().edits().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String insightKey(RepairCandidateSet candidateSet) {
        Set<String> keys = new LinkedHashSet<>();
        for (RepairCandidate candidate : candidateSet.candidates()) {
            keys.add(candidate.kind() + ":" + candidate.risk());
            keys.addAll(candidate.assumptionIds());
            for (SourceEdit edit : candidate.repair().edits()) {
                keys.add(editKey(edit));
            }
        }
        return keys.toString();
    }

    private Set<String> coverage(RepairCandidateSet candidateSet) {
        Set<String> coverage = new LinkedHashSet<>();
        for (RepairCandidate candidate : candidateSet.candidates()) {
            for (String assumptionId : candidate.assumptionIds()) {
                coverage.add("assumption:" + assumptionId);
            }
            for (SourceEdit edit : candidate.repair().edits()) {
                coverage.add("edit:" + editKey(edit));
            }
        }
        return coverage;
    }

    private String editKey(SourceEdit edit) {
        return edit.file() + ":" + edit.startOffset() + "-" + edit.endOffset();
    }
}
