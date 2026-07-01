import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: dereference.of.nullable
// @repair-mode: sketch
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
// @repair-plan-edits: 0
public class ReceiverSketchOnlyRepair {
    void f(@Nullable String s) {
        // :: error: (dereference.of.nullable)
        s.length();
    }
}
