package checker_reconcile.diagnosis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Connected diagnostics that should be considered as one repair/search unit. */
public final class DiagnosticGroup {
    private final List<String> diagnosticIds;
    private final Set<String> keys;

    public DiagnosticGroup(List<String> diagnosticIds, Set<String> keys) {
        this.diagnosticIds = Collections.unmodifiableList(new ArrayList<>(diagnosticIds));
        this.keys = Collections.unmodifiableSet(new LinkedHashSet<>(keys));
    }

    public List<String> diagnosticIds() {
        return diagnosticIds;
    }

    public Set<String> keys() {
        return keys;
    }
}
