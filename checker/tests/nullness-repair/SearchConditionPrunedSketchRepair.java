import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: condition.nullable
// @repair-mode: pass
// @repair-runner: search
// @repair-search-mode: pass
// @repair-include-sketch-edits: true
// @repair-allow-risk: BODY_CHANGE
// @repair-contains: if (b == null) {
// @repair-contains: throw new NullPointerException("b");
// @repair-contains: if (b) {}
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
public class SearchConditionPrunedSketchRepair {
    void f(@Nullable Boolean b) {
        // :: error: (condition.nullable)
        if (b) {}
    }
}
