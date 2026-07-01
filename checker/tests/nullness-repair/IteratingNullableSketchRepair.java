import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: iterating.over.nullable
// @repair-mode: sketch
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
// @repair-plan-edits: 0
public class IteratingNullableSketchRepair {
    void f(@Nullable Iterable<String> values) {
        // :: error: (iterating.over.nullable)
        for (String value : values) {}
    }
}
