import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: assignment.type.incompatible
// @repair-mode: sketch
// @repair-runner: search
// @repair-search-mode: pass
// @repair-search-accepted: false
// @repair-search-report: true
// @repair-search-generated-candidates: 7
// @repair-search-searched-candidates: 6
// @repair-search-pruned-budget: 1
// @repair-search-event: candidate_pruned
// @repair-search-report-contains: "reason":"budget"
// @repair-max-candidate-size: 3
// @repair-max-search-candidates: 6
// @repair-allow-risk: LOCAL_ONLY
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: LOCAL_ONLY
// @repair-plan-automatic: true
// @repair-plan-edits: 1
public class SearchThreeEditBudgetPrunedRepair {
    void f(@Nullable String s) {
        // :: error: (assignment.type.incompatible)
        @NonNull String first = s;
        // :: error: (assignment.type.incompatible)
        @NonNull String second = s;
        // :: error: (assignment.type.incompatible)
        @NonNull String third = s;
    }
}
