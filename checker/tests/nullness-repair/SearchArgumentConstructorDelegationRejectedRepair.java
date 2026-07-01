import org.checkerframework.checker.nullness.qual.Nullable;

// @repair-kind: argument.type.incompatible
// @repair-mode: sketch
// @repair-runner: search
// @repair-search-mode: pass
// @repair-search-accepted: false
// @repair-search-report: true
// @repair-search-pruned-empty: 2
// @repair-search-pruned-empty-reason: constructor delegation=1
// @repair-search-pruned-empty-reason: no edits=1
// @repair-search-event: candidate_pruned
// @repair-search-report-contains: "reason":"constructor delegation"
// @repair-search-report-contains: "agent_refactor_target":true
// @repair-search-report-contains: "refactor_context":"constructor_delegation"
// @repair-include-sketch-edits: true
// @repair-allow-risk: BODY_CHANGE
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
// @repair-plan-edits: 0
public class SearchArgumentConstructorDelegationRejectedRepair {
    SearchArgumentConstructorDelegationRejectedRepair(String p) {}

    SearchArgumentConstructorDelegationRejectedRepair(@Nullable String s, int ignored) {
        // :: error: (argument.type.incompatible)
        this(s);
    }
}
