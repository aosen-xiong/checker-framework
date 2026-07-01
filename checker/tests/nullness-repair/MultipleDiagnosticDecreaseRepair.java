import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: assignment.type.incompatible
// @repair-mode: decrease
// @repair-contains: @Nullable String first = s;
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: LOCAL_ONLY
// @repair-plan-automatic: true
// @repair-plan-edits: 1
public class MultipleDiagnosticDecreaseRepair {
    void f(@Nullable String s) {
        // :: error: (assignment.type.incompatible)
        @NonNull String first = s;
        // :: error: (assignment.type.incompatible)
        @NonNull String second = s;
    }
}
