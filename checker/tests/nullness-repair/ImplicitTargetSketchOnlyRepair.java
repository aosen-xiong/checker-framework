import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: argument.type.incompatible
// @repair-mode: pass
// @repair-runner: search
// @repair-search-mode: pass
// @repair-include-sketch-edits: true
// @repair-allow-risk: BODY_CHANGE
// @repair-contains: if (s == null) {
// @repair-contains: throw new NullPointerException("s");
// @repair-contains: takes(s);
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
public class ImplicitTargetSketchOnlyRepair {
    void takes(String p) {}

    void passes(@Nullable String s) {
        // :: error: (argument.type.incompatible)
        takes(s);
    }
}
