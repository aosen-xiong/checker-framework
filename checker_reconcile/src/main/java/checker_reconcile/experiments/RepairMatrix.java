package checker_reconcile.experiments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Summarizes file-based repair test metadata. */
public final class RepairMatrix {
    private static final String REPAIR_KIND_PREFIX = "// @repair-kind:";
    private static final String REPAIR_MODE_PREFIX = "// @repair-mode:";
    private static final String REPAIR_RUNNER_PREFIX = "// @repair-runner:";
    private static final String SEARCH_MODE_PREFIX = "// @repair-search-mode:";
    private static final String SEARCH_ACCEPTED_PREFIX = "// @repair-search-accepted:";
    private static final String SEARCH_CANDIDATE_SIZE_PREFIX = "// @repair-search-candidate-size:";
    private static final String SEARCH_REPORT_PREFIX = "// @repair-search-report:";
    private static final String SEARCH_PRUNED_DUPLICATE_PREFIX =
            "// @repair-search-pruned-duplicate:";
    private static final String SEARCH_PRUNED_OVERLAP_PREFIX = "// @repair-search-pruned-overlap:";
    private static final String SEARCH_PRUNED_BUDGET_PREFIX = "// @repair-search-pruned-budget:";
    private static final String MAX_CANDIDATE_SIZE_PREFIX = "// @repair-max-candidate-size:";
    private static final String MAX_SEARCH_CANDIDATES_PREFIX = "// @repair-max-search-candidates:";
    private static final String ALLOW_RISK_PREFIX = "// @repair-allow-risk:";
    private static final String PLAN_KIND_PREFIX = "// @repair-plan-kind:";
    private static final String PLAN_RISK_PREFIX = "// @repair-plan-risk:";
    private static final String PLAN_AUTOMATIC_PREFIX = "// @repair-plan-automatic:";
    private static final String PLAN_EDITS_PREFIX = "// @repair-plan-edits:";

    public Report summarize(Path testsRoot) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(testsRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> entries.add(parse(path)));
        }
        return new Report(testsRoot, entries);
    }

    private Entry parse(Path source) {
        String diagnosticKind = "";
        String mode = "pass";
        String runner = "patch";
        String searchMode = "";
        String searchAccepted = "";
        String searchCandidateSize = "";
        String searchReport = "";
        String searchPrunedDuplicate = "";
        String searchPrunedOverlap = "";
        String searchPrunedBudget = "";
        String maxCandidateSize = "";
        String maxSearchCandidates = "";
        String allowRisk = "";
        String planKind = "";
        String risk = "";
        String automatic = "";
        String edits = "";
        try {
            for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith(REPAIR_KIND_PREFIX)) {
                    diagnosticKind = value(trimmed, REPAIR_KIND_PREFIX);
                } else if (trimmed.startsWith(REPAIR_MODE_PREFIX)) {
                    mode = value(trimmed, REPAIR_MODE_PREFIX);
                } else if (trimmed.startsWith(REPAIR_RUNNER_PREFIX)) {
                    runner = value(trimmed, REPAIR_RUNNER_PREFIX);
                } else if (trimmed.startsWith(SEARCH_MODE_PREFIX)) {
                    searchMode = value(trimmed, SEARCH_MODE_PREFIX);
                } else if (trimmed.startsWith(SEARCH_ACCEPTED_PREFIX)) {
                    searchAccepted = value(trimmed, SEARCH_ACCEPTED_PREFIX);
                } else if (trimmed.startsWith(SEARCH_CANDIDATE_SIZE_PREFIX)) {
                    searchCandidateSize = value(trimmed, SEARCH_CANDIDATE_SIZE_PREFIX);
                } else if (trimmed.startsWith(SEARCH_REPORT_PREFIX)) {
                    searchReport = value(trimmed, SEARCH_REPORT_PREFIX);
                } else if (trimmed.startsWith(SEARCH_PRUNED_DUPLICATE_PREFIX)) {
                    searchPrunedDuplicate = value(trimmed, SEARCH_PRUNED_DUPLICATE_PREFIX);
                } else if (trimmed.startsWith(SEARCH_PRUNED_OVERLAP_PREFIX)) {
                    searchPrunedOverlap = value(trimmed, SEARCH_PRUNED_OVERLAP_PREFIX);
                } else if (trimmed.startsWith(SEARCH_PRUNED_BUDGET_PREFIX)) {
                    searchPrunedBudget = value(trimmed, SEARCH_PRUNED_BUDGET_PREFIX);
                } else if (trimmed.startsWith(MAX_CANDIDATE_SIZE_PREFIX)) {
                    maxCandidateSize = value(trimmed, MAX_CANDIDATE_SIZE_PREFIX);
                } else if (trimmed.startsWith(MAX_SEARCH_CANDIDATES_PREFIX)) {
                    maxSearchCandidates = value(trimmed, MAX_SEARCH_CANDIDATES_PREFIX);
                } else if (trimmed.startsWith(ALLOW_RISK_PREFIX)) {
                    allowRisk = value(trimmed, ALLOW_RISK_PREFIX);
                } else if (trimmed.startsWith(PLAN_KIND_PREFIX)) {
                    planKind = value(trimmed, PLAN_KIND_PREFIX);
                } else if (trimmed.startsWith(PLAN_RISK_PREFIX)) {
                    risk = value(trimmed, PLAN_RISK_PREFIX);
                } else if (trimmed.startsWith(PLAN_AUTOMATIC_PREFIX)) {
                    automatic = value(trimmed, PLAN_AUTOMATIC_PREFIX);
                } else if (trimmed.startsWith(PLAN_EDITS_PREFIX)) {
                    edits = value(trimmed, PLAN_EDITS_PREFIX);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("could not read repair test " + source, e);
        }
        if (diagnosticKind.isEmpty()) {
            throw new IllegalArgumentException(source + " is missing " + REPAIR_KIND_PREFIX);
        }
        return new Entry(
                source,
                diagnosticKind,
                mode,
                runner,
                searchMode,
                searchAccepted,
                searchCandidateSize,
                searchReport,
                searchPrunedDuplicate,
                searchPrunedOverlap,
                searchPrunedBudget,
                maxCandidateSize,
                maxSearchCandidates,
                allowRisk,
                planKind,
                risk,
                automatic,
                edits);
    }

    private String value(String line, String prefix) {
        return line.substring(prefix.length()).trim();
    }

    /** One parsed repair test entry. */
    public static final class Entry {
        private final Path source;
        private final String diagnosticKind;
        private final String mode;
        private final String runner;
        private final String searchMode;
        private final String searchAccepted;
        private final String searchCandidateSize;
        private final String searchReport;
        private final String searchPrunedDuplicate;
        private final String searchPrunedOverlap;
        private final String searchPrunedBudget;
        private final String maxCandidateSize;
        private final String maxSearchCandidates;
        private final String allowRisk;
        private final String planKind;
        private final String risk;
        private final String automatic;
        private final String edits;

        Entry(
                Path source,
                String diagnosticKind,
                String mode,
                String runner,
                String searchMode,
                String searchAccepted,
                String searchCandidateSize,
                String searchReport,
                String searchPrunedDuplicate,
                String searchPrunedOverlap,
                String searchPrunedBudget,
                String maxCandidateSize,
                String maxSearchCandidates,
                String allowRisk,
                String planKind,
                String risk,
                String automatic,
                String edits) {
            this.source = source;
            this.diagnosticKind = diagnosticKind;
            this.mode = mode;
            this.runner = runner;
            this.searchMode = searchMode;
            this.searchAccepted = searchAccepted;
            this.searchCandidateSize = searchCandidateSize;
            this.searchReport = searchReport;
            this.searchPrunedDuplicate = searchPrunedDuplicate;
            this.searchPrunedOverlap = searchPrunedOverlap;
            this.searchPrunedBudget = searchPrunedBudget;
            this.maxCandidateSize = maxCandidateSize;
            this.maxSearchCandidates = maxSearchCandidates;
            this.allowRisk = allowRisk;
            this.planKind = planKind;
            this.risk = risk;
            this.automatic = automatic;
            this.edits = edits;
        }

        public Path source() {
            return source;
        }

        public String diagnosticKind() {
            return diagnosticKind;
        }

        public String mode() {
            return mode;
        }

        public String runner() {
            return runner;
        }

        public String searchMode() {
            return searchMode;
        }

        public String searchAccepted() {
            return searchAccepted;
        }

        public String searchCandidateSize() {
            return searchCandidateSize;
        }

        public String searchReport() {
            return searchReport;
        }

        public String searchPrunedDuplicate() {
            return searchPrunedDuplicate;
        }

        public String searchPrunedOverlap() {
            return searchPrunedOverlap;
        }

        public String searchPrunedBudget() {
            return searchPrunedBudget;
        }

        public String maxCandidateSize() {
            return maxCandidateSize;
        }

        public String maxSearchCandidates() {
            return maxSearchCandidates;
        }

        public String allowRisk() {
            return allowRisk;
        }

        public String planKind() {
            return planKind;
        }

        public String risk() {
            return risk;
        }

        public String automatic() {
            return automatic;
        }

        public String edits() {
            return edits;
        }
    }

    /** Matrix summary and render helper. */
    public static final class Report {
        private final Path testsRoot;
        private final List<Entry> entries;

        Report(Path testsRoot, List<Entry> entries) {
            this.testsRoot = testsRoot;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        }

        public List<Entry> entries() {
            return entries;
        }

        public String render() {
            StringBuilder out = new StringBuilder();
            out.append("root: ").append(testsRoot).append('\n');
            out.append("total: ").append(entries.size()).append('\n');
            appendCounts(out, "diagnostic-kind", count(entries, Field.DIAGNOSTIC_KIND));
            appendCounts(out, "repair-mode", count(entries, Field.MODE));
            appendCounts(out, "runner", count(entries, Field.RUNNER));
            appendCounts(out, "search-mode", count(entries, Field.SEARCH_MODE));
            appendCounts(out, "search-accepted", count(entries, Field.SEARCH_ACCEPTED));
            appendCounts(out, "search-candidate-size", count(entries, Field.SEARCH_CANDIDATE_SIZE));
            appendCounts(out, "search-report", count(entries, Field.SEARCH_REPORT));
            appendCounts(
                    out, "search-pruned-duplicate", count(entries, Field.SEARCH_PRUNED_DUPLICATE));
            appendCounts(out, "search-pruned-overlap", count(entries, Field.SEARCH_PRUNED_OVERLAP));
            appendCounts(out, "search-pruned-budget", count(entries, Field.SEARCH_PRUNED_BUDGET));
            appendCounts(out, "max-candidate-size", count(entries, Field.MAX_CANDIDATE_SIZE));
            appendCounts(out, "max-search-candidates", count(entries, Field.MAX_SEARCH_CANDIDATES));
            appendCounts(out, "allow-risk", count(entries, Field.ALLOW_RISK));
            appendCounts(out, "plan-kind", count(entries, Field.PLAN_KIND));
            appendCounts(out, "risk", count(entries, Field.RISK));
            appendCounts(out, "automatic", count(entries, Field.AUTOMATIC));
            appendCounts(out, "edit-count", count(entries, Field.EDITS));
            return out.toString();
        }

        private void appendCounts(StringBuilder out, String title, Map<String, Integer> counts) {
            out.append(title).append(":\n");
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                out.append("  ")
                        .append(entry.getKey().isEmpty() ? "<missing>" : entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append('\n');
            }
        }

        private Map<String, Integer> count(List<Entry> entries, Field field) {
            Map<String, Integer> result = new LinkedHashMap<>();
            for (Entry entry : entries) {
                String key = field.value(entry);
                Integer count = result.get(key);
                result.put(key, count == null ? 1 : count + 1);
            }
            return result;
        }
    }

    private enum Field {
        DIAGNOSTIC_KIND {
            @Override
            String value(Entry entry) {
                return entry.diagnosticKind();
            }
        },
        MODE {
            @Override
            String value(Entry entry) {
                return entry.mode();
            }
        },
        RUNNER {
            @Override
            String value(Entry entry) {
                return entry.runner();
            }
        },
        SEARCH_MODE {
            @Override
            String value(Entry entry) {
                return entry.searchMode();
            }
        },
        SEARCH_ACCEPTED {
            @Override
            String value(Entry entry) {
                return entry.searchAccepted();
            }
        },
        SEARCH_CANDIDATE_SIZE {
            @Override
            String value(Entry entry) {
                return entry.searchCandidateSize();
            }
        },
        SEARCH_REPORT {
            @Override
            String value(Entry entry) {
                return entry.searchReport();
            }
        },
        SEARCH_PRUNED_DUPLICATE {
            @Override
            String value(Entry entry) {
                return entry.searchPrunedDuplicate();
            }
        },
        SEARCH_PRUNED_OVERLAP {
            @Override
            String value(Entry entry) {
                return entry.searchPrunedOverlap();
            }
        },
        SEARCH_PRUNED_BUDGET {
            @Override
            String value(Entry entry) {
                return entry.searchPrunedBudget();
            }
        },
        MAX_CANDIDATE_SIZE {
            @Override
            String value(Entry entry) {
                return entry.maxCandidateSize();
            }
        },
        MAX_SEARCH_CANDIDATES {
            @Override
            String value(Entry entry) {
                return entry.maxSearchCandidates();
            }
        },
        ALLOW_RISK {
            @Override
            String value(Entry entry) {
                return entry.allowRisk();
            }
        },
        PLAN_KIND {
            @Override
            String value(Entry entry) {
                return entry.planKind();
            }
        },
        RISK {
            @Override
            String value(Entry entry) {
                return entry.risk();
            }
        },
        AUTOMATIC {
            @Override
            String value(Entry entry) {
                return entry.automatic();
            }
        },
        EDITS {
            @Override
            String value(Entry entry) {
                return entry.edits();
            }
        };

        abstract String value(Entry entry);
    }
}
