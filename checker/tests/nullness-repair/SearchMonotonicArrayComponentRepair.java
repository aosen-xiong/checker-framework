import org.checkerframework.checker.nullness.qual.*;

// @repair-kind: assignment.type.incompatible
// @repair-mode: pass
// @repair-runner: search
// @repair-search-mode: pass
// @repair-include-sketch-edits: true
// @repair-allow-risk: LOCAL_ONLY
// @repair-contains: @Nullable Object[] o1 = new @MonotonicNonNull Object[10];
// @repair-plan-kind: CHANGE_QUALIFIER
// @repair-plan-risk: LOCAL_ONLY
// @repair-plan-automatic: false
public class SearchMonotonicArrayComponentRepair {
    void f() {
        @MonotonicNonNull Object[] o1 = new @MonotonicNonNull Object[10];
        // :: error: (assignment.type.incompatible)
        o1[0] = null;
    }
}
