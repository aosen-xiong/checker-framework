import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: iterating.over.nullable
// @repair-mode: pass
// @repair-runner: search
// @repair-search-mode: pass
// @repair-include-sketch-edits: true
// @repair-allow-risk: BODY_CHANGE
// @repair-contains: if (values == null) {
// @repair-contains: throw new NullPointerException("values");
// @repair-contains: for (String value : values) {}
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
public class SearchIteratingNullableNullCheckPassRepair {
    void f(@Nullable Iterable<String> values) {
        // :: error: (iterating.over.nullable)
        for (String value : values) {}
    }
}
