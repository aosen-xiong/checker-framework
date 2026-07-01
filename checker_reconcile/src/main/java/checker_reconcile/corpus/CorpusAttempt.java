package checker_reconcile.corpus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import checker_reconcile.diagnosis.ValidationBackedRepairSearch;
import checker_reconcile.repair.AgentRefactorTargetJson;
import checker_reconcile.repair.RepairKind;
import checker_reconcile.repair.RiskLevel;
import checker_reconcile.repair.Validation;

/** Result of one corpus repair attempt. */
public final class CorpusAttempt {
    private final CorpusCase corpusCase;
    private final boolean traceOk;
    private final boolean searchOk;
    private final boolean accepted;
    private final boolean decreased;
    private final boolean fullPass;
    private final String failureReason;
    private final String plannerReason;
    private final Set<RiskLevel> allowedRisks;
    private final RepairKind acceptedRepairKind;
    private final RiskLevel acceptedRisk;
    private final List<CorpusEdit> acceptedEdits;
    private final boolean validationCacheHit;
    private final boolean validationCacheMiss;
    private final Validation.Result before;
    private final Validation.Result after;
    private final ValidationBackedRepairSearch.SearchStats searchStats;
    private final Path tracePath;
    private final Path workSourcePath;
    private final Path patchedSourcePath;

    public CorpusAttempt(
            CorpusCase corpusCase,
            boolean traceOk,
            boolean searchOk,
            boolean accepted,
            boolean decreased,
            boolean fullPass,
            String failureReason,
            Set<RiskLevel> allowedRisks,
            RepairKind acceptedRepairKind,
            RiskLevel acceptedRisk,
            Validation.Result before,
            Validation.Result after,
            ValidationBackedRepairSearch.SearchStats searchStats) {
        this(
                corpusCase,
                traceOk,
                searchOk,
                accepted,
                decreased,
                fullPass,
                failureReason,
                "",
                allowedRisks,
                acceptedRepairKind,
                acceptedRisk,
                Collections.emptyList(),
                false,
                false,
                before,
                after,
                searchStats,
                null,
                null,
                null);
    }

    public CorpusAttempt(
            CorpusCase corpusCase,
            boolean traceOk,
            boolean searchOk,
            boolean accepted,
            boolean decreased,
            boolean fullPass,
            String failureReason,
            String plannerReason,
            Set<RiskLevel> allowedRisks,
            RepairKind acceptedRepairKind,
            RiskLevel acceptedRisk,
            List<CorpusEdit> acceptedEdits,
            boolean validationCacheHit,
            boolean validationCacheMiss,
            Validation.Result before,
            Validation.Result after,
            ValidationBackedRepairSearch.SearchStats searchStats) {
        this(
                corpusCase,
                traceOk,
                searchOk,
                accepted,
                decreased,
                fullPass,
                failureReason,
                plannerReason,
                allowedRisks,
                acceptedRepairKind,
                acceptedRisk,
                acceptedEdits,
                validationCacheHit,
                validationCacheMiss,
                before,
                after,
                searchStats,
                null,
                null,
                null);
    }

    public CorpusAttempt(
            CorpusCase corpusCase,
            boolean traceOk,
            boolean searchOk,
            boolean accepted,
            boolean decreased,
            boolean fullPass,
            String failureReason,
            String plannerReason,
            Set<RiskLevel> allowedRisks,
            RepairKind acceptedRepairKind,
            RiskLevel acceptedRisk,
            List<CorpusEdit> acceptedEdits,
            boolean validationCacheHit,
            boolean validationCacheMiss,
            Validation.Result before,
            Validation.Result after,
            ValidationBackedRepairSearch.SearchStats searchStats,
            Path tracePath,
            Path workSourcePath,
            Path patchedSourcePath) {
        this.corpusCase = corpusCase;
        this.traceOk = traceOk;
        this.searchOk = searchOk;
        this.accepted = accepted;
        this.decreased = decreased;
        this.fullPass = fullPass;
        this.failureReason = failureReason;
        this.plannerReason = plannerReason;
        this.allowedRisks = Collections.unmodifiableSet(new LinkedHashSet<>(allowedRisks));
        this.acceptedRepairKind = acceptedRepairKind;
        this.acceptedRisk = acceptedRisk;
        this.acceptedEdits = Collections.unmodifiableList(new ArrayList<>(acceptedEdits));
        this.validationCacheHit = validationCacheHit;
        this.validationCacheMiss = validationCacheMiss;
        this.before = before;
        this.after = after;
        this.searchStats = searchStats;
        this.tracePath = tracePath;
        this.workSourcePath = workSourcePath;
        this.patchedSourcePath = patchedSourcePath;
    }

    public CorpusCase corpusCase() {
        return corpusCase;
    }

    public boolean traceOk() {
        return traceOk;
    }

    public boolean searchOk() {
        return searchOk;
    }

    public boolean accepted() {
        return accepted;
    }

    public boolean decreased() {
        return decreased;
    }

    public boolean fullPass() {
        return fullPass;
    }

    public String failureReason() {
        return failureReason;
    }

    public String plannerReason() {
        return plannerReason;
    }

    public String agentRefactorContext() {
        return AgentRefactorTargetJson.refactorContext(plannerReason);
    }

    public boolean agentRefactorTarget() {
        return !agentRefactorContext().isEmpty();
    }

    public Set<RiskLevel> allowedRisks() {
        return allowedRisks;
    }

    public RepairKind acceptedRepairKind() {
        return acceptedRepairKind;
    }

    public RiskLevel acceptedRisk() {
        return acceptedRisk;
    }

    public List<CorpusEdit> acceptedEdits() {
        return acceptedEdits;
    }

    public boolean validationCacheHit() {
        return validationCacheHit;
    }

    public boolean validationCacheMiss() {
        return validationCacheMiss;
    }

    public Validation.Result before() {
        return before;
    }

    public Validation.Result after() {
        return after;
    }

    public ValidationBackedRepairSearch.SearchStats searchStats() {
        return searchStats;
    }

    public Path tracePath() {
        return tracePath;
    }

    public Path workSourcePath() {
        return workSourcePath;
    }

    public Path patchedSourcePath() {
        return patchedSourcePath;
    }
}
