package checker_reconcile.diagnosis;

/** Configuration for bounded diagnosis search. */
public final class SolverConfig {
    private final int maxSetSize;
    private final int maxCandidates;

    public SolverConfig(int maxSetSize, int maxCandidates) {
        this.maxSetSize = maxSetSize;
        this.maxCandidates = maxCandidates;
    }

    public static SolverConfig defaults() {
        return new SolverConfig(2, 20);
    }

    public int maxSetSize() {
        return maxSetSize;
    }

    public int maxCandidates() {
        return maxCandidates;
    }
}
