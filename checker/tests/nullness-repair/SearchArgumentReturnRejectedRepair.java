import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: argument.type.incompatible
// @repair-mode: sketch
// @repair-runner: search
// @repair-search-mode: pass
// @repair-search-accepted: false
// @repair-search-report: true
// @repair-search-pruned-empty: 2
// @repair-search-pruned-empty-reason: return expression=1
// @repair-search-pruned-empty-reason: no edits=1
// @repair-search-event: candidate_pruned
// @repair-search-report-contains: "reason":"return expression"
// @repair-search-report-contains: "agent_refactor_target":true
// @repair-search-report-contains: "refactor_context":"return_expression"
// @repair-include-sketch-edits: true
// @repair-allow-risk: BODY_CHANGE
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
// @repair-plan-edits: 0
public class SearchArgumentReturnRejectedRepair {
    String takes(String p) {
        return p;
    }

    String passes(@Nullable String s) {
        // :: error: (argument.type.incompatible)
        return takes(s);
    }
}
