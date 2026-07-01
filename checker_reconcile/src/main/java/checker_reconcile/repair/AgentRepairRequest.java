package checker_reconcile.repair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import checker_reconcile.constraints.TraceModel.DiagnosticSlice;

/** Context passed to an optional agent-assisted repair component. */
public final class AgentRepairRequest {
    private final Path source;
    private final DiagnosticSlice slice;
    private final List<SuggestedRepair> deterministicRepairs;

    public AgentRepairRequest(
            Path source, DiagnosticSlice slice, List<SuggestedRepair> deterministicRepairs) {
        this.source = source;
        this.slice = slice;
        this.deterministicRepairs =
                Collections.unmodifiableList(new ArrayList<>(deterministicRepairs));
    }

    public Path source() {
        return source;
    }

    public DiagnosticSlice slice() {
        return slice;
    }

    public List<SuggestedRepair> deterministicRepairs() {
        return deterministicRepairs;
    }
}
