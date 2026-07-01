package checker_reconcile.repair;

import java.io.IOException;
import java.util.List;

/** Optional extension point for model- or agent-assisted repair suggestions. */
public interface AgentRepairAdvisor {
    List<SuggestedRepair> advise(AgentRepairRequest request) throws IOException;
}
