import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: assignment.type.incompatible
// @repair-mode: pass
// @repair-runner: search
// @repair-search-mode: pass
// @repair-search-accepted: true
// @repair-search-candidate-size: 1
// @repair-search-report: true
// @repair-search-generated-candidates: 3
// @repair-search-searched-candidates: 1
// @repair-search-pruned-duplicate: 1
// @repair-search-pruned-overlap: 1
// @repair-search-event: candidate_pruned
// @repair-search-report-contains: "reason":"duplicate edits"
// @repair-search-report-contains: "reason":"overlapping edits"
// @repair-allow-risk: API_CHANGE
// @repair-contains: @Nullable String f = "";
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: API_CHANGE
// @repair-plan-automatic: true
// @repair-plan-edits: 1
public class SearchSharedFieldPrunedRepair {
    @NonNull String f = "";

    void assignFirst(@Nullable String s) {
        // :: error: (assignment.type.incompatible)
        this.f = s;
    }

    void assignSecond(@Nullable String s) {
        // :: error: (assignment.type.incompatible)
        this.f = s;
    }
}
