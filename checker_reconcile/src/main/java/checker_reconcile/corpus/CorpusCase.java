package checker_reconcile.corpus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import checker_reconcile.repair.RepairKind;

/** One source/diagnostic selected for corpus repair evaluation. */
public final class CorpusCase {
    private final Path root;
    private final Path source;
    private final String relativeSource;
    private final String diagnosticId;
    private final String diagnosticKind;
    private final List<RepairKind> possibleRepairKinds;
    private final Map<String, String> options;

    public CorpusCase(
            Path root,
            Path source,
            String relativeSource,
            String diagnosticId,
            String diagnosticKind,
            Map<String, String> options) {
        this(
                root,
                source,
                relativeSource,
                diagnosticId,
                diagnosticKind,
                Collections.emptyList(),
                options);
    }

    public CorpusCase(
            Path root,
            Path source,
            String relativeSource,
            String diagnosticId,
            String diagnosticKind,
            List<RepairKind> possibleRepairKinds,
            Map<String, String> options) {
        this.root = root;
        this.source = source;
        this.relativeSource = relativeSource;
        this.diagnosticId = diagnosticId;
        this.diagnosticKind = diagnosticKind;
        this.possibleRepairKinds =
                Collections.unmodifiableList(new ArrayList<>(possibleRepairKinds));
        this.options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    public Path root() {
        return root;
    }

    public Path source() {
        return source;
    }

    public String relativeSource() {
        return relativeSource;
    }

    public String diagnosticId() {
        return diagnosticId;
    }

    public String diagnosticKind() {
        return diagnosticKind;
    }

    public List<RepairKind> possibleRepairKinds() {
        return possibleRepairKinds;
    }

    public Map<String, String> options() {
        return options;
    }
}
