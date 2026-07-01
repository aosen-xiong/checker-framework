package checker_reconcile.diagnosis;

import checker_reconcile.repair.RiskLevel;

/** Comparable cost for ranking candidate repairs. */
public final class RepairCost implements Comparable<RepairCost> {
    private final int value;

    public RepairCost(int value) {
        this.value = value;
    }

    public static int riskPenalty(RiskLevel risk) {
        switch (risk) {
            case LOCAL_ONLY:
                return 0;
            case API_CHANGE:
                return 20;
            case BODY_CHANGE:
                return 40;
            case SUPPRESSION:
                return 80;
            case AGENT_ASSISTED:
                return 100;
            case UNKNOWN:
            default:
                return 200;
        }
    }

    public int value() {
        return value;
    }

    @Override
    public int compareTo(RepairCost other) {
        return Integer.compare(value, other.value);
    }
}
