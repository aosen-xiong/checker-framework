import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Queue;

// @repair-kind: assignment.type.incompatible
// @repair-mode: pass
// @repair-contains: @Nullable String firstNode = q.poll();
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: LOCAL_ONLY
// @repair-plan-automatic: true
// @repair-plan-edits: 1
public class CfDerivedQueuePollRepair {
    void mNoCheck(Queue<@Nullable String> q) {
        // :: error: (assignment.type.incompatible)
        @NonNull String firstNode = q.poll();
    }
}
