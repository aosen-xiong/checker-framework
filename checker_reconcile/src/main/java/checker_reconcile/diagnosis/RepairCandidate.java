package checker_reconcile.diagnosis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import checker_reconcile.repair.RepairKind;
import checker_reconcile.repair.RiskLevel;
import checker_reconcile.repair.SuggestedRepair;

/** One candidate repair with its diagnosis cost. */
public final class RepairCandidate {
    private final SuggestedRepair repair;
    private final List<String> assumptionIds;
    private final List<String> diagnosticIds;
    private final RepairCost cost;

    public RepairCandidate(SuggestedRepair repair, List<String> assumptionIds, RepairCost cost) {
        this(repair, assumptionIds, Collections.emptyList(), cost);
    }

    public RepairCandidate(
            SuggestedRepair repair,
            List<String> assumptionIds,
            List<String> diagnosticIds,
            RepairCost cost) {
        this.repair = repair;
        this.assumptionIds = Collections.unmodifiableList(new ArrayList<>(assumptionIds));
        this.diagnosticIds = Collections.unmodifiableList(new ArrayList<>(diagnosticIds));
        this.cost = cost;
    }

    public SuggestedRepair repair() {
        return repair;
    }

    public List<String> assumptionIds() {
        return assumptionIds;
    }

    public List<String> diagnosticIds() {
        return diagnosticIds;
    }

    public RepairCost cost() {
        return cost;
    }

    public RepairKind kind() {
        return repair.kind();
    }

    public RiskLevel risk() {
        return repair.risk();
    }

    public boolean automatic() {
        return repair.automatic();
    }

    public String message() {
        return repair.message();
    }
}
