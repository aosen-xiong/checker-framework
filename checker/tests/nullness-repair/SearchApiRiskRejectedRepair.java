import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: argument.type.incompatible
// @repair-mode: sketch
// @repair-runner: search
// @repair-search-mode: pass
// @repair-search-accepted: false
// @repair-allow-risk: LOCAL_ONLY
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: API_CHANGE
// @repair-plan-automatic: true
// @repair-plan-edits: 1
public class SearchApiRiskRejectedRepair {
    void takes(@NonNull String p) {}

    void passes(@Nullable String s) {
        // :: error: (argument.type.incompatible)
        takes(s);
    }
}
