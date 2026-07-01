import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: assignment.type.incompatible
// @repair-mode: pass
// @repair-runner: agent
// @repair-contains: @Nullable String value = s;
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: LOCAL_ONLY
// @repair-plan-automatic: true
// @repair-plan-edits: 1
public class AgentLocalPassRepair {
    void f(@Nullable String s) {
        // :: error: (assignment.type.incompatible)
        @NonNull String value = s;
    }
}
