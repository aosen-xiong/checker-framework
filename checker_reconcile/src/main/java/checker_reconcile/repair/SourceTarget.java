package checker_reconcile.repair;

import java.nio.file.Path;

/** A semantic source location that a repair plan may edit. */
public final class SourceTarget {
    private final Path file;
    private final int startOffset;
    private final int endOffset;
    private final SourceTargetKind kind;

    public SourceTarget(Path file, int startOffset, int endOffset, SourceTargetKind kind) {
        this.file = file;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.kind = kind;
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

    public SourceTargetKind kind() {
        return kind;
    }

    public String syntacticKind() {
        return kind.wireName();
    }

    public SourceEdit replaceWith(String replacement) {
        return new SourceEdit(file, startOffset, endOffset, replacement);
    }
}
