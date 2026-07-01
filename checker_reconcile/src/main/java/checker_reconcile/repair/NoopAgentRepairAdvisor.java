package checker_reconcile.repair;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** Default agent advisor for deterministic-only repair planning. */
public final class NoopAgentRepairAdvisor implements AgentRepairAdvisor {
    @Override
    public List<SuggestedRepair> advise(AgentRepairRequest request) throws IOException {
        return Collections.emptyList();
    }
}
