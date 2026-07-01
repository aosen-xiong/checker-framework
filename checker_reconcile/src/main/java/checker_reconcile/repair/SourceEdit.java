package checker_reconcile.repair;

import java.nio.file.Path;

/** A concrete textual source edit using character offsets in one source file. */
public final class SourceEdit {
    private final Path file;
    private final int startOffset;
    private final int endOffset;
    private final String replacement;

    public SourceEdit(Path file, int startOffset, int endOffset, String replacement) {
        this.file = file;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.replacement = replacement;
    }

    public Path file() {
        return file;
    }

    public int startOffset() {
        return startOffset;
    }

    public int endOffset() {
        return endOffset;
    }

    public String replacement() {
        return replacement;
    }
}
