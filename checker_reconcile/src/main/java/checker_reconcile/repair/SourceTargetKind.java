package checker_reconcile.repair;

/** Typed source-target kinds used by repair policy; wire names preserve the JSON schema. */
public enum SourceTargetKind {
    LOCAL_ANNOTATION("local_annotation", RiskLevel.LOCAL_ONLY),
    FIELD_ANNOTATION("field_annotation", RiskLevel.API_CHANGE),
    PARAMETER_ANNOTATION("parameter_annotation", RiskLevel.API_CHANGE),
    RETURN_ANNOTATION("return_annotation", RiskLevel.API_CHANGE),
    UNKNOWN("unknown", RiskLevel.UNKNOWN);

    private final String wireName;
    private final RiskLevel risk;

    SourceTargetKind(String wireName, RiskLevel risk) {
        this.wireName = wireName;
        this.risk = risk;
    }

    public String wireName() {
        return wireName;
    }

    public RiskLevel risk() {
        return risk;
    }

    public static SourceTargetKind fromWireName(String wireName) {
        for (SourceTargetKind kind : values()) {
            if (kind.wireName.equals(wireName)) {
                return kind;
            }
        }
        return UNKNOWN;
    }
}
