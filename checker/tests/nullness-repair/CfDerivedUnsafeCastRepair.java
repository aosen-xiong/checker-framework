import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: assignment.type.incompatible
// @repair-mode: pass
// @repair-contains: @Nullable String s1 = (String) x;
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: LOCAL_ONLY
// @repair-plan-automatic: true
// @repair-plan-edits: 1
public class CfDerivedUnsafeCastRepair {
    void testSuppression(@Nullable Object x) {
        // :: error: (assignment.type.incompatible)
        @NonNull String s1 = (String) x;
    }
}
