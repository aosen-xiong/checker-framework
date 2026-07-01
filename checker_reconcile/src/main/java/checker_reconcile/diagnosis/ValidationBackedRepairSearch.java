package checker_reconcile.diagnosis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import checker_reconcile.constraints.TraceModel;
import checker_reconcile.repair.PatchApplier;
import checker_reconcile.repair.RepairSketch;
import checker_reconcile.repair.RiskLevel;
import checker_reconcile.repair.SourceEdit;
import checker_reconcile.repair.SuggestedRepair;
import checker_reconcile.repair.Validation;
import checker_reconcile.trace.TraceParser;

/** Applies ranked repair candidates to temporary sources and accepts only validated patches. */
public final class ValidationBackedRepairSearch {
    public static final int DEFAULT_MAX_CANDIDATE_SIZE = 2;
    public static final int DEFAULT_MAX_SEARCH_CANDIDATES = 100;

    private final DiagnosisEngine diagnosisEngine;
    private final Validation validation;
    private final DiagnosticGrouper diagnosticGrouper;
    private final ValidationCache validationCache;

    public ValidationBackedRepairSearch() {
        this(
                new DiagnosisEngine(),
                new Validation(),
                new DiagnosticGrouper(),
                new ValidationCache());
    }

    public ValidationBackedRepairSearch(DiagnosisEngine diagnosisEngine, Validation validation) {
        this(diagnosisEngine, validation, new DiagnosticGrouper(), new ValidationCache());
    }

    public ValidationBackedRepairSearch(
            DiagnosisEngine diagnosisEngine,
            Validation validation,
            ValidationCache validationCache) {
        this(diagnosisEngine, validation, new DiagnosticGrouper(), validationCache);
    }

    public ValidationBackedRepairSearch(
            DiagnosisEngine diagnosisEngine,
            Validation validation,
            DiagnosticGrouper diagnosticGrouper) {
        this(diagnosisEngine, validation, diagnosticGrouper, new ValidationCache());
    }

    public ValidationBackedRepairSearch(
            DiagnosisEngine diagnosisEngine,
            Validation validation,
            DiagnosticGrouper diagnosticGrouper,
            ValidationCache validationCache) {
        this.diagnosisEngine = diagnosisEngine;
        this.validation = validation;
        this.diagnosticGrouper = diagnosticGrouper;
        this.validationCache = validationCache;
    }

    public Result search(
            Path source,
            TraceModel model,
            Path out,
            String javac,
            Path checker,
            String validationMode,
            RiskLevel allowedRisk)
            throws Exception {
        return search(
                source, model, out, javac, checker, validationMode, allowedRisk, Listener.NOOP);
    }

    public Result search(
            Path source,
            TraceModel model,
            Path out,
            String javac,
            Path checker,
            String validationMode,
            RiskLevel allowedRisk,
            Listener listener)
            throws Exception {
        return search(
                source,
                model,
                out,
                javac,
                checker,
                validationMode,
                allowedRisk,
                listener,
                DEFAULT_MAX_CANDIDATE_SIZE,
                DEFAULT_MAX_SEARCH_CANDIDATES);
    }

    public Result search(
            Path source,
            TraceModel model,
            Path out,
            String javac,
            Path checker,
            String validationMode,
            RiskLevel allowedRisk,
            Listener listener,
            int maxCandidateSize)
            throws Exception {
        return search(
                source,
                model,
                out,
                javac,
                checker,
                validationMode,
                allowedRisk,
                listener,
                maxCandidateSize,
                DEFAULT_MAX_SEARCH_CANDIDATES);
    }

    public Result search(
            Path source,
            TraceModel model,
            Path out,
            String javac,
            Path checker,
            String validationMode,
            RiskLevel allowedRisk,
            Listener listener,
            int maxCandidateSize,
            int maxSearchCandidates)
            throws Exception {
        return search(
                source,
                model,
                out,
                javac,
                checker,
                validationMode,
                allowedRisk,
                listener,
                maxCandidateSize,
                maxSearchCandidates,
                false);
    }

    public Result search(
            Path source,
            TraceModel model,
            Path out,
            String javac,
            Path checker,
            String validationMode,
            RiskLevel allowedRisk,
            Listener listener,
            int maxCandidateSize,
            int maxSearchCandidates,
            boolean includeSketchEdits)
            throws Exception {
        Set<RiskLevel> allowedRisks = null;
        if (allowedRisk != null) {
            allowedRisks = new LinkedHashSet<>();
            allowedRisks.add(allowedRisk);
        }
        return search(
                source,
                model,
                out,
                javac,
                checker,
                validationMode,
                allowedRisks,
                null,
                listener,
                maxCandidateSize,
                maxSearchCandidates,
                includeSketchEdits);
    }

    public Result search(
            Path source,
            TraceModel model,
            Path out,
            String javac,
            Path checker,
            String validationMode,
            Set<RiskLevel> allowedRisks,
            Set<String> diagnosticFilter,
            Listener listener,
            int maxCandidateSize,
            int maxSearchCandidates,
            boolean includeSketchEdits)
            throws Exception {
        ValidationBackedRepairSearch search =
                includeSketchEdits
                        ? new ValidationBackedRepairSearch(
                                new DiagnosisEngine(true),
                                validation,
                                diagnosticGrouper,
                                validationCache)
                        : this;
        return search.searchInternal(
                source,
                model,
                out,
                javac,
                checker,
                validationMode,
                allowedRisks,
                diagnosticFilter,
                listener,
                maxCandidateSize,
                maxSearchCandidates,
                includeSketchEdits,
                1);
    }

    public Result search(
            Path source,
            TraceModel model,
            Path out,
            String javac,
            Path checker,
            String validationMode,
            Set<RiskLevel> allowedRisks,
            Set<String> diagnosticFilter,
            Listener listener,
            int maxCandidateSize,
            int maxSearchCandidates,
            boolean includeSketchEdits,
            int searchRounds)
            throws Exception {
        ValidationBackedRepairSearch search =
                includeSketchEdits
                        ? new ValidationBackedRepairSearch(
                                new DiagnosisEngine(true),
                                validation,
                                diagnosticGrouper,
                                validationCache)
                        : this;
        return search.searchInternal(
                source,
                model,
                out,
                javac,
                checker,
                validationMode,
                allowedRisks,
                diagnosticFilter,
                listener,
                maxCandidateSize,
                maxSearchCandidates,
                includeSketchEdits,
                searchRounds);
    }

    private Result searchInternal(
            Path source,
            TraceModel model,
            Path out,
            String javac,
            Path checker,
            String validationMode,
            Set<RiskLevel> allowedRisks,
            Set<String> diagnosticFilter,
            Listener listener,
            int maxCandidateSize,
            int maxSearchCandidates,
            boolean includeSketchEdits,
            int searchRounds)
            throws Exception {
        validateSearchBounds(maxCandidateSize, maxSearchCandidates);
        if (searchRounds < 1) {
            throw new IllegalArgumentException("searchRounds must be at least 1");
        }
        Validation.Result before = validation.validateDetailed(javac, checker, source);
        SearchSpace searchSpace =
                candidateSets(
                        source, model, diagnosticFilter, maxCandidateSize, maxSearchCandidates);
        Set<String> validatedDiagnosticIds = new LinkedHashSet<>();
        Set<String> rejectedDiagnosticIds = new LinkedHashSet<>();
        Set<String> skippedDiagnosticIds = new LinkedHashSet<>();
        int prunedCandidateIndex = 1;
        for (PrunedCandidate prunedCandidate : searchSpace.prunedCandidates()) {
            listener.pruned(
                    prunedCandidateIndex, prunedCandidate.candidateSet(), prunedCandidate.reason());
            prunedCandidateIndex++;
        }
        int candidateIndex = 1;
        for (RepairCandidateSet candidateSet : searchSpace.candidates()) {
            List<SuggestedRepair> repairs =
                    applicableRepairs(candidateSet, allowedRisks, includeSketchEdits);
            if (repairs.isEmpty()) {
                listener.skipped(candidateIndex, candidateSet, "no allowed edits");
                skippedDiagnosticIds.addAll(candidateSet.diagnosticIds());
                candidateIndex++;
                continue;
            }
            try {
                String cacheKey = validationCache.key(source, validationMode, candidateSet);
                Validation.Result after = validationCache.get(cacheKey);
                Path candidate = null;
                if (after == null) {
                    candidate = materializeCandidate(source, repairs);
                    after = validation.validateDetailed(javac, checker, candidate);
                    validationCache.put(cacheKey, after);
                }
                boolean accepted = accepted(validationMode, before, after);
                listener.validated(candidateIndex, candidateSet, after, accepted);
                validatedDiagnosticIds.addAll(candidateSet.diagnosticIds());
                if (accepted) {
                    if (candidate == null) {
                        candidate = materializeCandidate(source, repairs);
                    }
                    Files.copy(candidate, out, StandardCopyOption.REPLACE_EXISTING);
                    return new Result(
                            true,
                            before,
                            after,
                            candidateSet,
                            searchSpace
                                    .stats()
                                    .withCoverage(
                                            new ArrayList<>(validatedDiagnosticIds),
                                            candidateSet.diagnosticIds(),
                                            new ArrayList<>(rejectedDiagnosticIds),
                                            new ArrayList<>(skippedDiagnosticIds)));
                }
                if (searchRounds > 1 && candidate != null) {
                    Result followup =
                            searchFollowup(
                                    candidate,
                                    out,
                                    javac,
                                    checker,
                                    validationMode,
                                    allowedRisks,
                                    listener,
                                    maxCandidateSize,
                                    maxSearchCandidates,
                                    includeSketchEdits,
                                    searchRounds - 1,
                                    before,
                                    after,
                                    candidateSet,
                                    searchSpace.stats());
                    if (followup.accepted()) {
                        return followup;
                    }
                }
                rejectedDiagnosticIds.addAll(candidateSet.diagnosticIds());
            } catch (IllegalArgumentException ignored) {
                listener.invalid(candidateIndex, candidateSet, ignored.getMessage());
                skippedDiagnosticIds.addAll(candidateSet.diagnosticIds());
                // Invalid edit combinations are not accepted search candidates.
            }
            candidateIndex++;
        }
        return new Result(
                false,
                before,
                before,
                null,
                searchSpace
                        .stats()
                        .withCoverage(
                                new ArrayList<>(validatedDiagnosticIds),
                                null,
                                new ArrayList<>(rejectedDiagnosticIds),
                                new ArrayList<>(skippedDiagnosticIds)));
    }

    private Result searchFollowup(
            Path candidate,
            Path out,
            String javac,
            Path checker,
            String validationMode,
            Set<RiskLevel> allowedRisks,
            Listener listener,
            int maxCandidateSize,
            int maxSearchCandidates,
            boolean includeSketchEdits,
            int remainingRounds,
            Validation.Result originalBefore,
            Validation.Result candidateAfter,
            RepairCandidateSet firstCandidateSet,
            SearchStats firstStats)
            throws Exception {
        Path trace = Files.createTempFile("checker-reconcile-followup-trace", ".jsonl");
        Validation.Result tracedAfter =
                validation.validateDetailed(javac, checker, candidate, trace);
        if (accepted(validationMode, originalBefore, tracedAfter)) {
            Files.copy(candidate, out, StandardCopyOption.REPLACE_EXISTING);
            return new Result(
                    true,
                    originalBefore,
                    tracedAfter,
                    firstCandidateSet,
                    firstStats.withCoverage(
                            firstCandidateSet.diagnosticIds(),
                            firstCandidateSet.diagnosticIds(),
                            Collections.emptyList(),
                            Collections.emptyList()));
        }
        TraceModel followupModel = TraceModel.fromEvents(new TraceParser().parse(trace));
        if (followupModel.diagnostics.isEmpty()) {
            return new Result(false, originalBefore, candidateAfter, null, firstStats);
        }
        Path followupOut = Files.createTempFile("checker-reconcile-followup-patched", ".java");
        Result followup =
                searchInternal(
                        candidate,
                        followupModel,
                        followupOut,
                        javac,
                        checker,
                        validationMode,
                        allowedRisks,
                        null,
                        listener,
                        maxCandidateSize,
                        maxSearchCandidates,
                        includeSketchEdits,
                        remainingRounds);
        if (!followup.accepted()) {
            return new Result(false, originalBefore, candidateAfter, null, firstStats);
        }
        Validation.Result finalAfter = validation.validateDetailed(javac, checker, followupOut);
        if (!accepted(validationMode, originalBefore, finalAfter)) {
            return new Result(false, originalBefore, finalAfter, null, firstStats);
        }
        Files.copy(followupOut, out, StandardCopyOption.REPLACE_EXISTING);
        List<RepairCandidate> combined = new ArrayList<>(firstCandidateSet.candidates());
        combined.addAll(followup.candidateSet().candidates());
        RepairCandidateSet combinedSet = new RepairCandidateSet(combined);
        return new Result(
                true,
                originalBefore,
                finalAfter,
                combinedSet,
                firstStats.withCoverage(
                        combinedSet.diagnosticIds(),
                        combinedSet.diagnosticIds(),
                        Collections.emptyList(),
                        Collections.emptyList()));
    }

    private SearchSpace candidateSets(
            Path source,
            TraceModel model,
            Set<String> diagnosticFilter,
            int maxCandidateSize,
            int maxSearchCandidates)
            throws IOException {
        if (maxCandidateSize < 1) {
            throw new IllegalArgumentException("maxCandidateSize must be at least 1");
        }
        if (maxSearchCandidates < 1) {
            throw new IllegalArgumentException("maxSearchCandidates must be at least 1");
        }
        List<RepairCandidateSet> result = new ArrayList<>();
        List<List<RepairCandidateSet>> allByDiagnostic = new ArrayList<>();
        List<String> selectedDiagnosticIds = selectedDiagnosticIds(model, diagnosticFilter);
        for (DiagnosticGroup group : diagnosticGrouper.group(model)) {
            for (String diagnosticId : group.diagnosticIds()) {
                if (!selectedDiagnosticIds.contains(diagnosticId)) {
                    continue;
                }
                TraceModel.DiagnosticSlice slice = model.slice(diagnosticId);
                List<RepairCandidateSet> sets = diagnosisEngine.diagnose(source, model, slice);
                if (!sets.isEmpty()) {
                    allByDiagnostic.add(sets);
                    addIfWithinBound(result, sets, maxCandidateSize);
                }
            }
        }
        combineCandidateSets(
                allByDiagnostic, 0, maxCandidateSize, new ArrayList<RepairCandidate>(), result);
        result.sort(
                Comparator.comparingInt((RepairCandidateSet set) -> set.cost().value())
                        .thenComparingInt(set -> set.candidates().size()));
        return pruneCandidateSets(
                source, result, selectedDiagnosticIds, maxCandidateSize, maxSearchCandidates);
    }

    private List<String> selectedDiagnosticIds(TraceModel model, Set<String> diagnosticFilter) {
        List<String> result = new ArrayList<>();
        for (String diagnosticId : model.diagnostics.keySet()) {
            if (diagnosticFilter == null
                    || diagnosticFilter.isEmpty()
                    || diagnosticFilter.contains(diagnosticId)) {
                result.add(diagnosticId);
            }
        }
        return result;
    }

    private void addIfWithinBound(
            List<RepairCandidateSet> result,
            List<RepairCandidateSet> candidateSets,
            int maxCandidateSize) {
        for (RepairCandidateSet candidateSet : candidateSets) {
            if (candidateSet.candidates().size() <= maxCandidateSize) {
                result.add(candidateSet);
            }
        }
    }

    private void combineCandidateSets(
            List<List<RepairCandidateSet>> byDiagnostic,
            int startDiagnostic,
            int maxCandidateSize,
            List<RepairCandidate> selected,
            List<RepairCandidateSet> result) {
        for (int i = startDiagnostic; i < byDiagnostic.size(); i++) {
            for (RepairCandidateSet nextSet : byDiagnostic.get(i)) {
                List<RepairCandidate> next = new ArrayList<>(selected);
                next.addAll(nextSet.candidates());
                int size = next.size();
                if (size > maxCandidateSize) {
                    continue;
                }
                if (size >= 2) {
                    result.add(new RepairCandidateSet(next));
                }
                if (size < maxCandidateSize) {
                    combineCandidateSets(byDiagnostic, i + 1, maxCandidateSize, next, result);
                }
            }
        }
    }

    private SearchSpace pruneCandidateSets(
            Path source,
            List<RepairCandidateSet> candidateSets,
            Iterable<String> allDiagnosticIds,
            int maxCandidateSize,
            int maxSearchCandidates) {
        List<RepairCandidateSet> pruned = new ArrayList<>();
        List<PrunedCandidate> prunedCandidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int emptyEditSetCount = 0;
        int duplicateEditSetCount = 0;
        int overlappingEditSetCount = 0;
        int budgetPrunedCount = 0;
        Map<String, Integer> emptyEditReasons = new LinkedHashMap<>();
        for (RepairCandidateSet candidateSet : candidateSets) {
            String key = editKey(candidateSet);
            if (key.isEmpty()) {
                emptyEditSetCount++;
                countEmptyEditReasons(emptyEditReasons, candidateSet);
                prunedCandidates.add(
                        new PrunedCandidate(candidateSet, emptyEditReason(candidateSet)));
                continue;
            }
            if (!seen.add(key)) {
                duplicateEditSetCount++;
                prunedCandidates.add(new PrunedCandidate(candidateSet, "duplicate edits"));
                continue;
            }
            if (hasOverlappingEdits(source, candidateSet)) {
                overlappingEditSetCount++;
                prunedCandidates.add(new PrunedCandidate(candidateSet, "overlapping edits"));
                continue;
            }
            if (pruned.size() >= maxSearchCandidates) {
                budgetPrunedCount++;
                prunedCandidates.add(new PrunedCandidate(candidateSet, "budget"));
                continue;
            }
            pruned.add(candidateSet);
        }
        return new SearchSpace(
                pruned,
                prunedCandidates,
                new SearchStats(
                        maxCandidateSize,
                        maxSearchCandidates,
                        candidateSets.size(),
                        pruned.size(),
                        emptyEditSetCount,
                        duplicateEditSetCount,
                        overlappingEditSetCount,
                        budgetPrunedCount,
                        emptyEditReasons,
                        diagnosticIds(allDiagnosticIds),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()));
    }

    private List<String> diagnosticIds(Iterable<String> diagnosticIds) {
        List<String> result = new ArrayList<>();
        for (String diagnosticId : diagnosticIds) {
            result.add(diagnosticId);
        }
        return result;
    }

    private void countEmptyEditReasons(
            Map<String, Integer> emptyEditReasons, RepairCandidateSet candidateSet) {
        boolean foundReason = false;
        for (RepairCandidate candidate : candidateSet.candidates()) {
            SuggestedRepair repair = candidate.repair();
            for (RepairSketch sketch : repair.sketches()) {
                if (!sketch.materializationFailure().isEmpty()) {
                    increment(emptyEditReasons, sketch.materializationFailure());
                    foundReason = true;
                }
            }
        }
        if (!foundReason) {
            increment(emptyEditReasons, "no edits");
        }
    }

    private void increment(Map<String, Integer> counts, String key) {
        Integer current = counts.get(key);
        counts.put(key, current == null ? 1 : current + 1);
    }

    private String emptyEditReason(RepairCandidateSet candidateSet) {
        List<String> reasons = new ArrayList<>();
        for (RepairCandidate candidate : candidateSet.candidates()) {
            SuggestedRepair repair = candidate.repair();
            for (RepairSketch sketch : repair.sketches()) {
                if (!sketch.materializationFailure().isEmpty()
                        && !reasons.contains(sketch.materializationFailure())) {
                    reasons.add(sketch.materializationFailure());
                }
            }
        }
        return reasons.isEmpty() ? "no edits" : String.join("; ", reasons);
    }

    private void validateSearchBounds(int maxCandidateSize, int maxSearchCandidates) {
        if (maxCandidateSize < 1) {
            throw new IllegalArgumentException("maxCandidateSize must be at least 1");
        }
        if (maxSearchCandidates < 1) {
            throw new IllegalArgumentException("maxSearchCandidates must be at least 1");
        }
    }

    private String editKey(RepairCandidateSet candidateSet) {
        List<String> edits = new ArrayList<>();
        for (RepairCandidate candidate : candidateSet.candidates()) {
            for (SourceEdit edit : candidate.repair().edits()) {
                edits.add(
                        edit.file()
                                + ":"
                                + edit.startOffset()
                                + ":"
                                + edit.endOffset()
                                + ":"
                                + edit.replacement());
            }
        }
        if (edits.isEmpty()) {
            return "";
        }
        Collections.sort(edits);
        return edits.toString();
    }

    private boolean hasOverlappingEdits(Path source, RepairCandidateSet candidateSet) {
        List<SourceEdit> edits = new ArrayList<>();
        for (RepairCandidate candidate : candidateSet.candidates()) {
            edits.addAll(candidate.repair().edits());
        }
        Collections.sort(edits, Comparator.comparingInt(SourceEdit::startOffset));
        Set<String> exactRanges = new HashSet<>();
        int previousEnd = -1;
        for (SourceEdit edit : edits) {
            if (!source.equals(edit.file())) {
                return true;
            }
            String range = edit.startOffset() + ":" + edit.endOffset();
            if (!exactRanges.add(range) || edit.startOffset() < previousEnd) {
                return true;
            }
            previousEnd = edit.endOffset();
        }
        return false;
    }

    private List<SuggestedRepair> applicableRepairs(
            RepairCandidateSet candidateSet,
            Set<RiskLevel> allowedRisks,
            boolean includeSketchEdits) {
        List<SuggestedRepair> repairs = new ArrayList<>();
        for (RepairCandidate candidate : candidateSet.candidates()) {
            SuggestedRepair repair = candidate.repair();
            if (repair.edits().isEmpty()) {
                return Collections.emptyList();
            }
            if (!repair.automatic() && !includeSketchEdits) {
                return Collections.emptyList();
            }
            if (allowedRisks != null && !allowedRisks.contains(repair.risk())) {
                return Collections.emptyList();
            }
            repairs.add(repair);
        }
        return repairs;
    }

    private Path materializeCandidate(Path source, List<SuggestedRepair> repairs)
            throws IOException {
        Path candidateDir = Files.createTempDirectory("checker-reconcile-search");
        Path fileName = source.getFileName();
        Path candidate =
                fileName == null
                        ? candidateDir.resolve("Candidate.java")
                        : candidateDir.resolve(fileName);
        List<SourceEdit> edits = new ArrayList<>();
        for (SuggestedRepair repair : repairs) {
            edits.addAll(repair.edits());
        }
        new PatchApplier().writePatched(source, candidate, edits);
        return candidate;
    }

    private boolean accepted(
            String validationMode, Validation.Result before, Validation.Result after) {
        if (validationMode.equals("pass")) {
            return after.exitCode() == 0;
        }
        if (validationMode.equals("decrease")) {
            return after.diagnosticCount() < before.diagnosticCount();
        }
        throw new IllegalArgumentException("unknown validation mode: " + validationMode);
    }

    /** Observes validation-backed search attempts. */
    public interface Listener {
        Listener NOOP =
                new Listener() {
                    @Override
                    public void skipped(
                            int index, RepairCandidateSet candidateSet, String reason) {}

                    @Override
                    public void validated(
                            int index,
                            RepairCandidateSet candidateSet,
                            Validation.Result after,
                            boolean accepted) {}

                    @Override
                    public void invalid(
                            int index, RepairCandidateSet candidateSet, String reason) {}

                    @Override
                    public void pruned(int index, RepairCandidateSet candidateSet, String reason) {}
                };

        void skipped(int index, RepairCandidateSet candidateSet, String reason);

        void validated(
                int index,
                RepairCandidateSet candidateSet,
                Validation.Result after,
                boolean accepted);

        void invalid(int index, RepairCandidateSet candidateSet, String reason);

        default void pruned(int index, RepairCandidateSet candidateSet, String reason) {}
    }

    /** Result of validation-backed search. */
    public static final class Result {
        private final boolean accepted;
        private final Validation.Result before;
        private final Validation.Result after;
        private final RepairCandidateSet candidateSet;
        private final SearchStats searchStats;

        public Result(
                boolean accepted,
                Validation.Result before,
                Validation.Result after,
                RepairCandidateSet candidateSet,
                SearchStats searchStats) {
            this.accepted = accepted;
            this.before = before;
            this.after = after;
            this.candidateSet = candidateSet;
            this.searchStats = searchStats;
        }

        public boolean accepted() {
            return accepted;
        }

        public Validation.Result before() {
            return before;
        }

        public Validation.Result after() {
            return after;
        }

        public RepairCandidateSet candidateSet() {
            return candidateSet;
        }

        public SearchStats searchStats() {
            return searchStats;
        }
    }

    /** Memoizes validation results for identical source edit sets. */
    public static final class ValidationCache {
        private final Map<String, Validation.Result> results = new LinkedHashMap<>();
        private int hitCount;
        private int missCount;

        private String key(Path source, String validationMode, RepairCandidateSet candidateSet) {
            return source.toAbsolutePath().normalize()
                    + "|"
                    + validationMode
                    + "|"
                    + editKey(candidateSet);
        }

        private Validation.Result get(String key) {
            Validation.Result result = results.get(key);
            if (result == null) {
                missCount++;
            } else {
                hitCount++;
            }
            return result;
        }

        private void put(String key, Validation.Result result) {
            results.put(key, result);
        }

        public int hitCount() {
            return hitCount;
        }

        public int missCount() {
            return missCount;
        }

        public int uniqueValidationCount() {
            return results.size();
        }

        private String editKey(RepairCandidateSet candidateSet) {
            List<String> edits = new ArrayList<>();
            for (RepairCandidate candidate : candidateSet.candidates()) {
                for (SourceEdit edit : candidate.repair().edits()) {
                    edits.add(
                            edit.file()
                                    + ":"
                                    + edit.startOffset()
                                    + ":"
                                    + edit.endOffset()
                                    + ":"
                                    + edit.replacement());
                }
            }
            Collections.sort(edits);
            return edits.toString();
        }
    }

    private static final class SearchSpace {
        private final List<RepairCandidateSet> candidates;
        private final List<PrunedCandidate> prunedCandidates;
        private final SearchStats stats;

        private SearchSpace(
                List<RepairCandidateSet> candidates,
                List<PrunedCandidate> prunedCandidates,
                SearchStats stats) {
            this.candidates = candidates;
            this.prunedCandidates = prunedCandidates;
            this.stats = stats;
        }

        private List<RepairCandidateSet> candidates() {
            return candidates;
        }

        private List<PrunedCandidate> prunedCandidates() {
            return prunedCandidates;
        }

        private SearchStats stats() {
            return stats;
        }
    }

    private static final class PrunedCandidate {
        private final RepairCandidateSet candidateSet;
        private final String reason;

        private PrunedCandidate(RepairCandidateSet candidateSet, String reason) {
            this.candidateSet = candidateSet;
            this.reason = reason;
        }

        private RepairCandidateSet candidateSet() {
            return candidateSet;
        }

        private String reason() {
            return reason;
        }
    }

    /** Search-space accounting for validation-backed repair search. */
    public static final class SearchStats {
        private final int maxCandidateSize;
        private final int maxSearchCandidates;
        private final int generatedCandidateCount;
        private final int searchedCandidateCount;
        private final int prunedEmptyEditCount;
        private final int prunedDuplicateEditCount;
        private final int prunedOverlapCount;
        private final int prunedBudgetCount;
        private final Map<String, Integer> prunedEmptyEditReasons;
        private final List<String> allDiagnosticIds;
        private final List<String> validatedDiagnosticIds;
        private final List<String> acceptedDiagnosticIds;
        private final List<String> rejectedDiagnosticIds;
        private final List<String> skippedDiagnosticIds;
        private final List<String> uncoveredDiagnosticIds;

        public SearchStats(
                int maxCandidateSize,
                int maxSearchCandidates,
                int generatedCandidateCount,
                int searchedCandidateCount,
                int prunedEmptyEditCount,
                int prunedDuplicateEditCount,
                int prunedOverlapCount,
                int prunedBudgetCount,
                Map<String, Integer> prunedEmptyEditReasons,
                List<String> allDiagnosticIds,
                List<String> validatedDiagnosticIds,
                List<String> acceptedDiagnosticIds,
                List<String> rejectedDiagnosticIds,
                List<String> skippedDiagnosticIds) {
            this.maxCandidateSize = maxCandidateSize;
            this.maxSearchCandidates = maxSearchCandidates;
            this.generatedCandidateCount = generatedCandidateCount;
            this.searchedCandidateCount = searchedCandidateCount;
            this.prunedEmptyEditCount = prunedEmptyEditCount;
            this.prunedDuplicateEditCount = prunedDuplicateEditCount;
            this.prunedOverlapCount = prunedOverlapCount;
            this.prunedBudgetCount = prunedBudgetCount;
            this.prunedEmptyEditReasons =
                    Collections.unmodifiableMap(new LinkedHashMap<>(prunedEmptyEditReasons));
            this.allDiagnosticIds = immutableDistinct(allDiagnosticIds);
            this.validatedDiagnosticIds = immutableDistinct(validatedDiagnosticIds);
            this.acceptedDiagnosticIds = immutableDistinct(acceptedDiagnosticIds);
            this.rejectedDiagnosticIds =
                    minusAccepted(
                            immutableDistinct(rejectedDiagnosticIds), this.acceptedDiagnosticIds);
            this.skippedDiagnosticIds =
                    minusAccepted(
                            immutableDistinct(skippedDiagnosticIds), this.acceptedDiagnosticIds);
            this.uncoveredDiagnosticIds =
                    uncovered(this.allDiagnosticIds, this.validatedDiagnosticIds);
        }

        private SearchStats withCoverage(
                List<String> validatedDiagnosticIds,
                List<String> acceptedDiagnosticIds,
                List<String> rejectedDiagnosticIds,
                List<String> skippedDiagnosticIds) {
            return new SearchStats(
                    maxCandidateSize,
                    maxSearchCandidates,
                    generatedCandidateCount,
                    searchedCandidateCount,
                    prunedEmptyEditCount,
                    prunedDuplicateEditCount,
                    prunedOverlapCount,
                    prunedBudgetCount,
                    prunedEmptyEditReasons,
                    allDiagnosticIds,
                    validatedDiagnosticIds,
                    acceptedDiagnosticIds == null ? Collections.emptyList() : acceptedDiagnosticIds,
                    rejectedDiagnosticIds,
                    skippedDiagnosticIds);
        }

        private List<String> immutableDistinct(List<String> values) {
            return Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(values)));
        }

        private List<String> minusAccepted(
                List<String> values, List<String> acceptedDiagnosticIds) {
            Set<String> accepted = new LinkedHashSet<>(acceptedDiagnosticIds);
            List<String> result = new ArrayList<>();
            for (String value : values) {
                if (!accepted.contains(value)) {
                    result.add(value);
                }
            }
            return Collections.unmodifiableList(result);
        }

        private List<String> uncovered(List<String> all, List<String> validated) {
            Set<String> covered = new LinkedHashSet<>(validated);
            List<String> result = new ArrayList<>();
            for (String diagnosticId : all) {
                if (!covered.contains(diagnosticId)) {
                    result.add(diagnosticId);
                }
            }
            return Collections.unmodifiableList(result);
        }

        public int maxCandidateSize() {
            return maxCandidateSize;
        }

        public int maxSearchCandidates() {
            return maxSearchCandidates;
        }

        public int generatedCandidateCount() {
            return generatedCandidateCount;
        }

        public int searchedCandidateCount() {
            return searchedCandidateCount;
        }

        public int prunedEmptyEditCount() {
            return prunedEmptyEditCount;
        }

        public int prunedDuplicateEditCount() {
            return prunedDuplicateEditCount;
        }

        public int prunedOverlapCount() {
            return prunedOverlapCount;
        }

        public int prunedBudgetCount() {
            return prunedBudgetCount;
        }

        public Map<String, Integer> prunedEmptyEditReasons() {
            return prunedEmptyEditReasons;
        }

        public List<String> allDiagnosticIds() {
            return allDiagnosticIds;
        }

        public List<String> validatedDiagnosticIds() {
            return validatedDiagnosticIds;
        }

        public List<String> acceptedDiagnosticIds() {
            return acceptedDiagnosticIds;
        }

        public List<String> rejectedDiagnosticIds() {
            return rejectedDiagnosticIds;
        }

        public List<String> skippedDiagnosticIds() {
            return skippedDiagnosticIds;
        }

        public List<String> uncoveredDiagnosticIds() {
            return uncoveredDiagnosticIds;
        }
    }
}
