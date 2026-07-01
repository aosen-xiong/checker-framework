import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: argument.type.incompatible
// @repair-mode: pass
// @repair-contains: void takes(@Nullable String p)
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: API_CHANGE
// @repair-plan-automatic: true
// @repair-plan-edits: 1
public class ExplicitParameterRepair {
    void takes(@NonNull String p) {}

    void passes(@Nullable String s) {
        // :: error: (argument.type.incompatible)
        takes(s);
    }
}
