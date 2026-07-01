import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: argument.type.incompatible
// @repair-mode: pass
// @repair-runner: search
// @repair-search-mode: pass
// @repair-include-sketch-edits: true
// @repair-allow-risk: BODY_CHANGE
// @repair-contains-raw:        if (s == null) {
// @repair-contains: throw new NullPointerException("s");
// @repair-contains: takesWithEnoughArgumentsToStayMultilineAfterFormatting(
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
public class SearchArgumentMultilineNullCheckPassRepair {
    void takesWithEnoughArgumentsToStayMultilineAfterFormatting(
            String firstParameterWithLongName,
            String secondParameterWithLongName,
            String nullableParameter) {}

    void passes(@Nullable String s) {
        // :: error: (argument.type.incompatible)
        takesWithEnoughArgumentsToStayMultilineAfterFormatting(
                "first argument with enough text to keep this invocation wrapped",
                "second argument with enough text to keep this invocation wrapped",
                s);
    }
}
