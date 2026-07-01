import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: assignment.type.incompatible
// @repair-mode: pass
// @repair-runner: search
// @repair-search-mode: pass
// @repair-search-candidate-size: 3
// @repair-max-candidate-size: 3
// @repair-allow-risk: LOCAL_ONLY
// @repair-contains: @Nullable String first = s;
// @repair-contains: @Nullable String second = s;
// @repair-contains: @Nullable String third = s;
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: LOCAL_ONLY
// @repair-plan-automatic: true
// @repair-plan-edits: 1
public class SearchThreeEditPassRepair {
    void f(@Nullable String s) {
        // :: error: (assignment.type.incompatible)
        @NonNull String first = s;
        // :: error: (assignment.type.incompatible)
        @NonNull String second = s;
        // :: error: (assignment.type.incompatible)
        @NonNull String third = s;
    }
}
