import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: argument.type.incompatible
// @repair-mode: pass
// @repair-runner: search
// @repair-search-mode: pass
// @repair-include-sketch-edits: true
// @repair-allow-risk: BODY_CHANGE
// @repair-contains-raw:        if (s == null) {
// @repair-contains: throw new NullPointerException("s");
// @repair-contains: new SearchArgumentConstructorStandaloneNullCheckPassRepair(s);
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
public class SearchArgumentConstructorStandaloneNullCheckPassRepair {
    SearchArgumentConstructorStandaloneNullCheckPassRepair(String p) {}

    void passes(@Nullable String s) {
        // :: error: (argument.type.incompatible)
        new SearchArgumentConstructorStandaloneNullCheckPassRepair(s);
    }
}
