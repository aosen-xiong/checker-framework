package checker_reconcile.repair;

/** Coarse risk classification for suggested repairs. */
public enum RiskLevel {
    LOCAL_ONLY,
    API_CHANGE,
    BODY_CHANGE,
    SUPPRESSION,
    AGENT_ASSISTED,
    UNKNOWN
}
