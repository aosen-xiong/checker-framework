import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: condition.nullable
// @repair-mode: sketch
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
// @repair-plan-edits: 0
public class ConditionNullableSketchRepair {
    void f(@Nullable Boolean b) {
        // :: error: (condition.nullable)
        if (b) {}
    }
}
