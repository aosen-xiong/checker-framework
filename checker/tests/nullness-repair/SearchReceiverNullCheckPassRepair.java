import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: dereference.of.nullable
// @repair-mode: pass
// @repair-runner: search
// @repair-search-mode: pass
// @repair-include-sketch-edits: true
// @repair-allow-risk: BODY_CHANGE
// @repair-contains: if (s == null) {
// @repair-contains: throw new NullPointerException("s");
// @repair-contains: s.length();
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
public class SearchReceiverNullCheckPassRepair {
    void f(@Nullable String s) {
        // :: error: (dereference.of.nullable)
        s.length();
    }
}
