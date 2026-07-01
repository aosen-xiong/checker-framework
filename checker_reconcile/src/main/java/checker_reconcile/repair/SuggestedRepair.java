package checker_reconcile.repair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A typed repair plan with concrete edits only when the repair is directly patchable. */
public final class SuggestedRepair {
    private final RepairKind kind;
    private final List<SourceEdit> edits;
    private final RiskLevel risk;
    private final boolean automatic;
    private final List<String> evidenceIds;
    private final String message;
    private final List<RepairSketch> sketches;

    public SuggestedRepair(
            RepairKind kind,
            List<SourceEdit> edits,
            RiskLevel risk,
            boolean automatic,
            List<String> evidenceIds,
            String message) {
        this(kind, edits, risk, automatic, evidenceIds, message, Collections.emptyList());
    }

    public SuggestedRepair(
            RepairKind kind,
            List<SourceEdit> edits,
            RiskLevel risk,
            boolean automatic,
            List<String> evidenceIds,
            String message,
            List<RepairSketch> sketches) {
        this.kind = kind;
        this.edits = Collections.unmodifiableList(new ArrayList<>(edits));
        this.risk = risk;
        this.automatic = automatic;
        this.evidenceIds = Collections.unmodifiableList(new ArrayList<>(evidenceIds));
        this.message = message;
        this.sketches = Collections.unmodifiableList(new ArrayList<>(sketches));
    }

    public RepairKind kind() {
        return kind;
    }

    public List<SourceEdit> edits() {
        return edits;
    }

    public RiskLevel risk() {
        return risk;
    }

    public boolean automatic() {
        return automatic;
    }

    public List<String> evidenceIds() {
        return evidenceIds;
    }

    public String message() {
        return message;
    }

    public List<RepairSketch> sketches() {
        return sketches;
    }
}
