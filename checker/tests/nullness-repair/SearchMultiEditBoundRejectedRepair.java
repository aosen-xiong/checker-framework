import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: assignment.type.incompatible
// @repair-mode: sketch
// @repair-runner: search
// @repair-search-mode: pass
// @repair-search-accepted: false
// @repair-max-candidate-size: 1
// @repair-allow-risk: LOCAL_ONLY
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: LOCAL_ONLY
// @repair-plan-automatic: true
// @repair-plan-edits: 1
public class SearchMultiEditBoundRejectedRepair {
    void f(@Nullable String s) {
        // :: error: (assignment.type.incompatible)
        @NonNull String first = s;
        // :: error: (assignment.type.incompatible)
        @NonNull String second = s;
    }
}
