import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: assignment.type.incompatible
// @repair-mode: pass
// @repair-contains: @Nullable String test = s;
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: LOCAL_ONLY
// @repair-plan-automatic: true
// @repair-plan-edits: 1
public class CfDerivedCompoundFlowRepair {
    void test(@Nullable String s) {
        if (s == null || s.length() > 0) {
            // :: error: (assignment.type.incompatible)
            @NonNull String test = s;
        }
    }
}
