package checker_reconcile.repair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Applies concrete source edits. This class intentionally knows nothing about repair semantics. */
public final class PatchApplier {
    public void writePatched(Path source, Path out, List<SourceEdit> edits) throws IOException {
        if (edits.isEmpty()) {
            Files.copy(source, out, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        List<SourceEdit> orderedEdits = new ArrayList<>(edits);
        Collections.sort(orderedEdits, Comparator.comparingInt(SourceEdit::startOffset).reversed());
        validateEdits(source, text.length(), orderedEdits);

        StringBuilder builder = new StringBuilder(text);
        for (SourceEdit edit : orderedEdits) {
            builder.replace(edit.startOffset(), edit.endOffset(), edit.replacement());
        }

        Files.write(out, builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void validateEdits(Path source, int sourceLength, List<SourceEdit> orderedEdits) {
        int lastStart = sourceLength + 1;
        for (SourceEdit edit : orderedEdits) {
            if (!source.equals(edit.file())) {
                throw new IllegalArgumentException(
                        "selected source edit targets a different file: " + edit.file());
            }
            if (edit.startOffset() < 0
                    || edit.endOffset() < edit.startOffset()
                    || edit.endOffset() > sourceLength) {
                throw new IllegalArgumentException("selected source edit has invalid range");
            }
            if (edit.endOffset() > lastStart) {
                throw new IllegalArgumentException("selected source edits overlap");
            }
            lastStart = edit.startOffset();
        }
    }
}
