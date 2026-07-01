import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: unboxing.of.nullable
// @repair-mode: sketch
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
// @repair-plan-edits: 0
public class UnboxingNullableSketchRepair {
    void f(@Nullable Integer value) {
        // :: error: (unboxing.of.nullable)
        int unboxed = value;
    }
}
