package checker_reconcile.diagnosis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A selected set of assumptions whose change may correct a diagnostic. */
public final class CorrectionSet {
    private final List<String> assumptionIds;
    private final RepairCost cost;

    public CorrectionSet(List<String> assumptionIds, RepairCost cost) {
        this.assumptionIds = Collections.unmodifiableList(new ArrayList<>(assumptionIds));
        this.cost = cost;
    }

    public List<String> assumptionIds() {
        return assumptionIds;
    }

    public RepairCost cost() {
        return cost;
    }
}
