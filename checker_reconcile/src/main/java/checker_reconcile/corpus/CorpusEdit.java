package checker_reconcile.corpus;

/** One accepted source edit recorded in a corpus attempt. */
public final class CorpusEdit {
    private final int startOffset;
    private final int endOffset;
    private final String original;
    private final String replacement;

    public CorpusEdit(int startOffset, int endOffset, String original, String replacement) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.original = original;
        this.replacement = replacement;
    }

    public int startOffset() {
        return startOffset;
    }

    public int endOffset() {
        return endOffset;
    }

    public String original() {
        return original;
    }

    public String replacement() {
        return replacement;
    }

    public String key() {
        return original + " -> " + replacement + " @ " + startOffset + ":" + endOffset;
    }
}
