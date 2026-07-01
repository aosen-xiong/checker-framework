package checker_reconcile.experiments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import checker_reconcile.corpus.CfTestMetadata;
import checker_reconcile.repair.RepairKind;

/** Mines Checker Framework test files for Nullness diagnostic examples. */
public final class CfTestDiagnosticMiner {
    private static final Pattern ERROR_PATTERN =
            Pattern.compile(
                    "::\\s*error:\\s*\\(?"
                            + "(assignment\\.type\\.incompatible"
                            + "|return\\.type\\.incompatible"
                            + "|argument\\.type\\.incompatible"
                            + "|method\\.invocation\\.invalid"
                            + "|dereference\\.of\\.nullable"
                            + "|condition\\.nullable"
                            + "|unboxing\\.of\\.nullable"
                            + "|accessing\\.nullable"
                            + "|iterating\\.over\\.nullable)"
                            + "\\)?");
    private static final Pattern EXPLICIT_NONNULL_LOCAL =
            Pattern.compile("^(?:final\\s+)?@NonNull\\b.*=.*");

    public List<Candidate> mine(Path root) throws IOException {
        List<Candidate> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(
                            path -> {
                                try {
                                    if (CfTestMetadata.isJavaTestFile(path)) {
                                        mineFile(root, path, result);
                                    }
                                } catch (IOException e) {
                                    throw new IllegalStateException(e);
                                }
                            });
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
        return result;
    }

    public Map<String, Integer> countsByKind(List<Candidate> candidates) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            Integer count = result.get(candidate.kind());
            result.put(candidate.kind(), count == null ? 1 : count + 1);
        }
        return result;
    }

    public List<Candidate> firstPerKind(List<Candidate> candidates) {
        Map<String, Candidate> first = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            if (!first.containsKey(candidate.kind())) {
                first.put(candidate.kind(), candidate);
            }
        }
        List<Candidate> result = new ArrayList<>(first.values());
        result.sort(Comparator.comparing(Candidate::kind));
        return result;
    }

    public List<Candidate> likelyLocalAnnotationRepairs(List<Candidate> candidates) {
        List<Candidate> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.likelyLocalAnnotationRepair()) {
                result.add(candidate);
            }
        }
        return result;
    }

    public List<Candidate> likelyRepairCandidates(List<Candidate> candidates) {
        List<Candidate> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (!candidate.likelyRepairKinds().isEmpty()) {
                result.add(candidate);
            }
        }
        return result;
    }

    private void mineFile(Path root, Path path, List<Candidate> result) throws IOException {
        List<String> lines = Files.readAllLines(path);
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = ERROR_PATTERN.matcher(lines.get(i));
            while (matcher.find()) {
                String codeLine = nextCodeLine(lines, i + 1);
                result.add(
                        new Candidate(
                                root.relativize(path).toString(),
                                i + 1,
                                matcher.group(1),
                                codeLine));
            }
        }
    }

    private String nextCodeLine(List<String> lines, int start) {
        for (int i = start; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }
            return trimmed;
        }
        return "";
    }

    /** One expected diagnostic marker in a Checker Framework test file. */
    public static final class Candidate {
        private final String file;
        private final int line;
        private final String kind;
        private final String codeLine;

        public Candidate(String file, int line, String kind) {
            this(file, line, kind, "");
        }

        public Candidate(String file, int line, String kind, String codeLine) {
            this.file = file;
            this.line = line;
            this.kind = kind;
            this.codeLine = codeLine;
        }

        public String file() {
            return file;
        }

        public int line() {
            return line;
        }

        public String kind() {
            return kind;
        }

        public String codeLine() {
            return codeLine;
        }

        public boolean likelyLocalAnnotationRepair() {
            return likelyQualifierWeakeningRepair()
                    && codeLine.contains("=")
                    && !codeLine.startsWith("return ")
                    && !codeLine.startsWith("this.")
                    && !codeLine.startsWith("super.");
        }

        public RepairKind likelyRepairKind() {
            List<RepairKind> kinds = likelyRepairKinds();
            return kinds.isEmpty() ? null : kinds.get(0);
        }

        public List<RepairKind> likelyRepairKinds() {
            List<RepairKind> result = new ArrayList<>();
            if (likelyQualifierWeakeningRepair() || mayResolveToApiQualifierWeakening()) {
                result.add(RepairKind.CHANGE_QUALIFIER);
            }
            if (likelyNullCheckRepair()) {
                result.add(RepairKind.ADD_NULL_CHECK);
            }
            return result;
        }

        public boolean likelyQualifierWeakeningRepair() {
            return kind.equals("assignment.type.incompatible")
                    && EXPLICIT_NONNULL_LOCAL.matcher(codeLine).find()
                    && !codeLine.contains("(@NonNull")
                    && !codeLine.contains("->");
        }

        public boolean likelyNullCheckRepair() {
            return kind.equals("argument.type.incompatible")
                    || kind.equals("return.type.incompatible")
                    || kind.equals("method.invocation.invalid")
                    || kind.equals("dereference.of.nullable")
                    || kind.equals("condition.nullable")
                    || kind.equals("unboxing.of.nullable")
                    || kind.equals("accessing.nullable")
                    || kind.equals("iterating.over.nullable");
        }

        private boolean mayResolveToApiQualifierWeakening() {
            return kind.equals("argument.type.incompatible")
                    || kind.equals("return.type.incompatible");
        }

        @Override
        public String toString() {
            return file + ":" + line + " " + kind;
        }
    }
}
