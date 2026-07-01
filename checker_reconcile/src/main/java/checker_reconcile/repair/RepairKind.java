package checker_reconcile.repair;

/** Logical repair kinds, independent of how source text is changed. */
public enum RepairKind {
    CHANGE_QUALIFIER,
    ADD_NULL_CHECK,
    INTRODUCE_SUPPRESSION,
    REFACTOR
}
