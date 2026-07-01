import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: assignment.type.incompatible
// @repair-mode: pass
// @repair-runner: search
// @repair-search-mode: pass
// @repair-allow-risk: API_CHANGE
// @repair-contains: @Nullable String f = "";
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: API_CHANGE
// @repair-plan-automatic: true
// @repair-plan-edits: 1
public class SearchApiFieldRepair {
    @NonNull String f = "";

    void assign(@Nullable String s) {
        // :: error: (assignment.type.incompatible)
        this.f = s;
    }
}
