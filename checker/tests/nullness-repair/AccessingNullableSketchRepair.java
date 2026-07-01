import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: accessing.nullable
// @repair-mode: pass
// @repair-runner: search
// @repair-search-mode: pass
// @repair-include-sketch-edits: true
// @repair-allow-risk: BODY_CHANGE
// @repair-contains: if (values == null) {
// @repair-contains: throw new NullPointerException("values");
// @repair-contains: String first = values[0];
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
public class AccessingNullableSketchRepair {
    void f(String @Nullable [] values) {
        // :: error: (accessing.nullable)
        String first = values[0];
    }
}
