package checker_reconcile.corpus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import checker_reconcile.constraints.TraceModel;
import checker_reconcile.diagnosis.DiagnosisEngine;
import checker_reconcile.diagnosis.RepairCandidate;
import checker_reconcile.diagnosis.ValidationBackedRepairSearch;
import checker_reconcile.experiments.CfTestDiagnosticMiner;
import checker_reconcile.repair.RepairKind;
import checker_reconcile.repair.RiskLevel;
import checker_reconcile.repair.SourceEdit;
import checker_reconcile.repair.Validation;
import checker_reconcile.trace.TraceEvent;
import checker_reconcile.trace.TraceParser;

/** Runs corpus-scale trace-guided repair attempts. */
public final class CorpusRunner {
    private static final List<String> DEFAULT_NULLNESS_OPTIONS =
            List.of(
                    "-AcheckPurityAnnotations",
                    "-AconservativeArgumentNullnessAfterInvocation=true",
                    "-Xlint:deprecation",
                    "-Alint=soundArrayCreationNullness,redundantNullComparison",
                    "-AajavaChecks",
                    "-AconvertTypeArgInferenceCrashToWarning=false");

    public static List<String> defaultNullnessOptions() {
        return DEFAULT_NULLNESS_OPTIONS;
    }

    public List<CorpusAttempt> run(Options options) throws Exception {
        Files.createDirectories(options.workDir());
        CfTestDiagnosticMiner miner = new CfTestDiagnosticMiner();
        List<CfTestDiagnosticMiner.Candidate> mined = miner.mine(options.root());
        Map<String, List<RepairKind>> possibleRepairKinds = possibleRepairKinds(mined);
        List<Path> sources = distinctSources(options.root(), mined);
        List<CorpusAttempt> attempts = new ArrayList<>();
        int sourceIndex = 0;
        for (Path source : sources) {
            if (attempts.size() >= options.limit()) {
                break;
            }
            sourceIndex++;
            progress(
                    options,
                    "source "
                            + sourceIndex
                            + "/"
                            + sources.size()
                            + " "
                            + options.root().relativize(source));
            Path sourceWorkDir = options.workDir().resolve("source-" + sourceIndex);
            Files.createDirectories(sourceWorkDir);
            Path copiedSource = sourceWorkDir.resolve(source.getFileName());
            Files.copy(source, copiedSource, StandardCopyOption.REPLACE_EXISTING);
            Path trace = sourceWorkDir.resolve("trace.jsonl");
            String relativeSource = options.root().relativize(source).toString();
            int traceExit =
                    exportTrace(options, copiedSource, trace, sourceWorkDir.resolve("classes"));
            if (!Files.isRegularFile(trace)) {
                attempts.add(
                        failedAttempt(
                                options,
                                source,
                                relativeSource,
                                "",
                                "",
                                "trace export failed with exit " + traceExit));
                continue;
            }
            TraceModel model = TraceModel.fromEvents(new TraceParser().parse(trace));
            if (model.diagnostics.isEmpty()) {
                attempts.add(
                        failedAttempt(
                                options,
                                source,
                                relativeSource,
                                "",
                                "",
                                "trace contained no diagnostics"));
                continue;
            }
            ValidationBackedRepairSearch.ValidationCache validationCache =
                    new ValidationBackedRepairSearch.ValidationCache();
            for (TraceEvent diagnostic : model.diagnostics.values()) {
                if (attempts.size() >= options.limit()) {
                    break;
                }
                progress(
                        options,
                        "attempt "
                                + (attempts.size() + 1)
                                + "/"
                                + options.limit()
                                + " "
                                + relativeSource
                                + "#"
                                + diagnostic.id
                                + " "
                                + diagnostic.stringField("error_kind"));
                attempts.add(
                        attemptDiagnostic(
                                options,
                                source,
                                copiedSource,
                                relativeSource,
                                model,
                                diagnostic,
                                possibleRepairKinds,
                                sourceWorkDir,
                                validationCache,
                                trace));
            }
        }
        return attempts;
    }

    private void progress(Options options, String message) {
        if (options.progress()) {
            System.err.println("[corpus] " + message);
        }
    }

    private CorpusAttempt attemptDiagnostic(
            Options options,
            Path originalSource,
            Path copiedSource,
            String relativeSource,
            TraceModel model,
            TraceEvent diagnostic,
            Map<String, List<RepairKind>> possibleRepairKinds,
            Path sourceWorkDir,
            ValidationBackedRepairSearch.ValidationCache validationCache,
            Path trace)
            throws Exception {
        CorpusCase corpusCase =
                new CorpusCase(
                        options.root(),
                        originalSource,
                        relativeSource,
                        diagnostic.id,
                        diagnostic.stringField("error_kind"),
                        possibleRepairKinds(
                                possibleRepairKinds,
                                relativeSource,
                                diagnostic.stringField("error_kind")),
                        metadata(options));
        Path patched =
                sourceWorkDir.resolve(
                        "patched-" + diagnostic.id + "-" + copiedSource.getFileName().toString());
        Set<String> diagnosticFilter = new LinkedHashSet<>();
        diagnosticFilter.add(diagnostic.id);
        int cacheHitsBefore = validationCache.hitCount();
        int cacheMissesBefore = validationCache.missCount();
        ValidationBackedRepairSearch.Result result =
                new ValidationBackedRepairSearch(
                                new DiagnosisEngine(),
                                new Validation(
                                        options.verboseDiagnostics(),
                                        options.javacOptions(),
                                        options.classpathExtra()),
                                validationCache)
                        .search(
                                copiedSource,
                                model,
                                patched,
                                options.javac(),
                                options.checkerJar(),
                                options.validationMode(),
                                options.allowedRisks(),
                                diagnosticFilter,
                                ValidationBackedRepairSearch.Listener.NOOP,
                                options.maxCandidateSize(),
                                options.maxSearchCandidates(),
                                options.includeSketchEdits(),
                                options.searchRounds());
        RepairKind acceptedKind = null;
        RiskLevel acceptedRisk = null;
        if (result.candidateSet() != null && !result.candidateSet().candidates().isEmpty()) {
            RepairCandidate candidate = result.candidateSet().candidates().get(0);
            acceptedKind = candidate.kind();
            acceptedRisk = candidate.risk();
        }
        List<CorpusEdit> acceptedEdits =
                result.accepted() ? acceptedEdits(copiedSource, result) : Collections.emptyList();
        boolean decreased = result.after().diagnosticCount() < result.before().diagnosticCount();
        boolean fullPass = result.after().exitCode() == 0;
        String failureReason = "";
        if (!result.accepted()) {
            failureReason = failureReason(result.searchStats());
        }
        String plannerReason = result.accepted() ? "" : plannerReason(result.searchStats());
        return new CorpusAttempt(
                corpusCase,
                true,
                true,
                result.accepted(),
                decreased,
                fullPass,
                failureReason,
                plannerReason,
                options.allowedRisks(),
                acceptedKind,
                acceptedRisk,
                acceptedEdits,
                validationCache.hitCount() > cacheHitsBefore,
                validationCache.missCount() > cacheMissesBefore,
                result.before(),
                result.after(),
                result.searchStats(),
                trace,
                copiedSource,
                patched);
    }

    private List<CorpusEdit> acceptedEdits(
            Path copiedSource, ValidationBackedRepairSearch.Result result) throws IOException {
        if (result.candidateSet() == null) {
            return Collections.emptyList();
        }
        String sourceText = Files.readString(copiedSource, StandardCharsets.UTF_8);
        List<CorpusEdit> edits = new ArrayList<>();
        for (RepairCandidate candidate : result.candidateSet().candidates()) {
            for (SourceEdit edit : candidate.repair().edits()) {
                if (!copiedSource.equals(edit.file())) {
                    continue;
                }
                String original = sourceText.substring(edit.startOffset(), edit.endOffset());
                edits.add(
                        new CorpusEdit(
                                edit.startOffset(),
                                edit.endOffset(),
                                original,
                                edit.replacement()));
            }
        }
        return edits;
    }

    private String plannerReason(ValidationBackedRepairSearch.SearchStats stats) {
        if (stats.generatedCandidateCount() == 0) {
            return "no repair candidates generated";
        }
        if (stats.prunedEmptyEditCount() > 0 && stats.searchedCandidateCount() == 0) {
            return "only sketch or unmaterialized repairs: "
                    + String.join(", ", stats.prunedEmptyEditReasons().keySet());
        }
        if (stats.prunedDuplicateEditCount() > 0 && stats.searchedCandidateCount() == 0) {
            return "all candidates duplicate edits";
        }
        if (stats.prunedOverlapCount() > 0 && stats.searchedCandidateCount() == 0) {
            return "all candidates overlapping edits";
        }
        if (stats.prunedBudgetCount() > 0 && stats.searchedCandidateCount() == 0) {
            return "all candidates outside search budget";
        }
        if (!stats.skippedDiagnosticIds().isEmpty()) {
            return "candidate repairs filtered by risk or automatic policy";
        }
        if (!stats.rejectedDiagnosticIds().isEmpty()) {
            return "candidate repairs failed validation";
        }
        if (!stats.uncoveredDiagnosticIds().isEmpty()) {
            return "diagnostics not covered by validated candidates";
        }
        return "unknown";
    }

    private String failureReason(ValidationBackedRepairSearch.SearchStats stats) {
        if (stats.generatedCandidateCount() == 0) {
            return "no candidates";
        }
        if (stats.searchedCandidateCount() == 0) {
            return "all candidates pruned";
        }
        if (!stats.skippedDiagnosticIds().isEmpty()) {
            return "no allowed edits";
        }
        return "validation rejected";
    }

    private CorpusAttempt failedAttempt(
            Options options,
            Path source,
            String relativeSource,
            String diagnosticId,
            String diagnosticKind,
            String reason) {
        CorpusCase corpusCase =
                new CorpusCase(
                        options.root(),
                        source,
                        relativeSource,
                        diagnosticId,
                        diagnosticKind,
                        Collections.emptyList(),
                        metadata(options));
        return new CorpusAttempt(
                corpusCase,
                false,
                false,
                false,
                false,
                false,
                reason,
                reason,
                options.allowedRisks(),
                null,
                null,
                Collections.emptyList(),
                false,
                false,
                null,
                null,
                null);
    }

    private List<Path> distinctSources(Path root, List<CfTestDiagnosticMiner.Candidate> candidates)
            throws IOException {
        Set<Path> seen = new LinkedHashSet<>();
        for (CfTestDiagnosticMiner.Candidate candidate : candidates) {
            Path source = root.resolve(candidate.file());
            if (CfTestMetadata.isJavaTestFile(source)) {
                seen.add(source);
            }
        }
        return new ArrayList<>(seen);
    }

    private Map<String, List<RepairKind>> possibleRepairKinds(
            List<CfTestDiagnosticMiner.Candidate> candidates) {
        Map<String, List<RepairKind>> result = new LinkedHashMap<>();
        for (CfTestDiagnosticMiner.Candidate candidate : candidates) {
            String key = corpusCaseKey(candidate.file(), candidate.kind());
            List<RepairKind> kinds = result.computeIfAbsent(key, unused -> new ArrayList<>());
            for (RepairKind kind : candidate.likelyRepairKinds()) {
                if (!kinds.contains(kind)) {
                    kinds.add(kind);
                }
            }
        }
        return result;
    }

    private List<RepairKind> possibleRepairKinds(
            Map<String, List<RepairKind>> possibleRepairKinds,
            String relativeSource,
            String diagnosticKind) {
        List<RepairKind> result =
                possibleRepairKinds.get(corpusCaseKey(relativeSource, diagnosticKind));
        return result == null ? Collections.emptyList() : result;
    }

    private String corpusCaseKey(String relativeSource, String diagnosticKind) {
        return relativeSource + "#" + diagnosticKind;
    }

    private int exportTrace(Options options, Path source, Path trace, Path classes)
            throws IOException, InterruptedException {
        Files.createDirectories(classes);
        List<String> command = new ArrayList<>();
        command.add(options.javac());
        addCheckerFrameworkJavacJvmOptions(command);
        command.add("-processor");
        command.add("org.checkerframework.checker.nullness.NullnessChecker");
        command.add("-AexportNullnessTrace=" + trace);
        command.add("-d");
        command.add(classes.toString());
        if (options.checkerJar() != null) {
            command.add("-cp");
            command.add(classpath(options));
            command.add("-processorpath");
            command.add(options.checkerJar().toString());
        } else if (!options.classpathExtra().isEmpty()) {
            command.add("-cp");
            command.add(classpath(options));
        }
        command.addAll(options.javacOptions());
        command.add(source.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] outputBytes = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        String output = new String(outputBytes, StandardCharsets.UTF_8);
        if (options.verboseDiagnostics() && !output.isEmpty()) {
            System.err.print(output);
        }
        return exitCode;
    }

    private String classpath(Options options) {
        List<String> entries = new ArrayList<>();
        if (options.checkerJar() != null) {
            entries.add(options.checkerJar().toString());
        }
        for (Path path : options.classpathExtra()) {
            entries.add(path.toString());
        }
        return String.join(File.pathSeparator, entries);
    }

    private Map<String, String> metadata(Options options) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("validation_mode", options.validationMode());
        result.put("search_rounds", Integer.toString(options.searchRounds()));
        result.put("javac_options", String.join(" ", options.javacOptions()));
        if (!options.classpathExtra().isEmpty()) {
            List<String> paths = new ArrayList<>();
            for (Path path : options.classpathExtra()) {
                paths.add(path.toString());
            }
            result.put("classpath_extra", String.join(File.pathSeparator, paths));
        }
        return result;
    }

    private void addCheckerFrameworkJavacJvmOptions(List<String> command) {
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
        command.add("-J--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED");
    }

    /** Corpus runner options. */
    public static final class Options {
        private final Path root;
        private final Path workDir;
        private final String javac;
        private final Path checkerJar;
        private final String validationMode;
        private final Set<RiskLevel> allowedRisks;
        private final int maxCandidateSize;
        private final int maxSearchCandidates;
        private final int limit;
        private final boolean includeSketchEdits;
        private final int searchRounds;
        private final boolean verboseDiagnostics;
        private final boolean progress;
        private final List<String> javacOptions;
        private final List<Path> classpathExtra;

        public Options(
                Path root,
                Path workDir,
                String javac,
                Path checkerJar,
                String validationMode,
                Set<RiskLevel> allowedRisks,
                int maxCandidateSize,
                int maxSearchCandidates,
                int limit,
                boolean includeSketchEdits) {
            this(
                    root,
                    workDir,
                    javac,
                    checkerJar,
                    validationMode,
                    allowedRisks,
                    maxCandidateSize,
                    maxSearchCandidates,
                    limit,
                    includeSketchEdits,
                    1,
                    false,
                    false,
                    DEFAULT_NULLNESS_OPTIONS,
                    Collections.emptyList());
        }

        public Options(
                Path root,
                Path workDir,
                String javac,
                Path checkerJar,
                String validationMode,
                Set<RiskLevel> allowedRisks,
                int maxCandidateSize,
                int maxSearchCandidates,
                int limit,
                boolean includeSketchEdits,
                boolean verboseDiagnostics) {
            this(
                    root,
                    workDir,
                    javac,
                    checkerJar,
                    validationMode,
                    allowedRisks,
                    maxCandidateSize,
                    maxSearchCandidates,
                    limit,
                    includeSketchEdits,
                    1,
                    verboseDiagnostics,
                    false,
                    DEFAULT_NULLNESS_OPTIONS,
                    Collections.emptyList());
        }

        public Options(
                Path root,
                Path workDir,
                String javac,
                Path checkerJar,
                String validationMode,
                Set<RiskLevel> allowedRisks,
                int maxCandidateSize,
                int maxSearchCandidates,
                int limit,
                boolean includeSketchEdits,
                boolean verboseDiagnostics,
                List<String> javacOptions,
                List<Path> classpathExtra) {
            this(
                    root,
                    workDir,
                    javac,
                    checkerJar,
                    validationMode,
                    allowedRisks,
                    maxCandidateSize,
                    maxSearchCandidates,
                    limit,
                    includeSketchEdits,
                    1,
                    verboseDiagnostics,
                    false,
                    javacOptions,
                    classpathExtra);
        }

        public Options(
                Path root,
                Path workDir,
                String javac,
                Path checkerJar,
                String validationMode,
                Set<RiskLevel> allowedRisks,
                int maxCandidateSize,
                int maxSearchCandidates,
                int limit,
                boolean includeSketchEdits,
                int searchRounds,
                boolean verboseDiagnostics,
                List<String> javacOptions,
                List<Path> classpathExtra) {
            this(
                    root,
                    workDir,
                    javac,
                    checkerJar,
                    validationMode,
                    allowedRisks,
                    maxCandidateSize,
                    maxSearchCandidates,
                    limit,
                    includeSketchEdits,
                    searchRounds,
                    verboseDiagnostics,
                    false,
                    javacOptions,
                    classpathExtra);
        }

        public Options(
                Path root,
                Path workDir,
                String javac,
                Path checkerJar,
                String validationMode,
                Set<RiskLevel> allowedRisks,
                int maxCandidateSize,
                int maxSearchCandidates,
                int limit,
                boolean includeSketchEdits,
                int searchRounds,
                boolean verboseDiagnostics,
                boolean progress,
                List<String> javacOptions,
                List<Path> classpathExtra) {
            this.root = root;
            this.workDir = workDir;
            this.javac = javac;
            this.checkerJar = checkerJar;
            this.validationMode = validationMode;
            this.allowedRisks = Collections.unmodifiableSet(new LinkedHashSet<>(allowedRisks));
            this.maxCandidateSize = maxCandidateSize;
            this.maxSearchCandidates = maxSearchCandidates;
            this.limit = limit;
            this.includeSketchEdits = includeSketchEdits;
            this.searchRounds = searchRounds;
            this.verboseDiagnostics = verboseDiagnostics;
            this.progress = progress;
            this.javacOptions = Collections.unmodifiableList(new ArrayList<>(javacOptions));
            this.classpathExtra = Collections.unmodifiableList(new ArrayList<>(classpathExtra));
        }

        public Path root() {
            return root;
        }

        public Path workDir() {
            return workDir;
        }

        public String javac() {
            return javac;
        }

        public Path checkerJar() {
            return checkerJar;
        }

        public String validationMode() {
            return validationMode;
        }

        public Set<RiskLevel> allowedRisks() {
            return allowedRisks;
        }

        public int maxCandidateSize() {
            return maxCandidateSize;
        }

        public int maxSearchCandidates() {
            return maxSearchCandidates;
        }

        public int limit() {
            return limit;
        }

        public boolean includeSketchEdits() {
            return includeSketchEdits;
        }

        public int searchRounds() {
            return searchRounds;
        }

        public boolean verboseDiagnostics() {
            return verboseDiagnostics;
        }

        public boolean progress() {
            return progress;
        }

        public List<String> javacOptions() {
            return javacOptions;
        }

        public List<Path> classpathExtra() {
            return classpathExtra;
        }
    }
}
