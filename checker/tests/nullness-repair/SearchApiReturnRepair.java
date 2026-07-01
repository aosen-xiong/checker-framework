import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: return.type.incompatible
// @repair-mode: pass
// @repair-runner: search
// @repair-search-mode: pass
// @repair-allow-risk: API_CHANGE
// @repair-contains: @Nullable String returns(@Nullable String s)
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: API_CHANGE
// @repair-plan-automatic: true
// @repair-plan-edits: 1
public class SearchApiReturnRepair {
    @NonNull String returns(@Nullable String s) {
        // :: error: (return.type.incompatible)
        return s;
    }
}
