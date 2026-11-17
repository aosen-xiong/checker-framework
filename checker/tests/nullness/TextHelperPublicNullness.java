// @below-java17-jdk-skip-test
// Public clients of nullable replacement/encoding helpers retain nonnull results.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.Spliterator;

@AnnotatedFor("nullness")
class TextHelperPublicNullness {
    int strings(String text, @Nullable Object value, int[] codePoints) {
        String converted = new String(codePoints, 0, codePoints.length);
        String concatenated = "prefix" + value;
        return converted.length()
                + concatenated.length()
                + text.replace('a', 'b').length()
                + text.replace("a", "b").length()
                + text.trim().length()
                + text.strip().length()
                + text.stripLeading().length()
                + text.stripTrailing().length();
    }

    long guarded(String text) {
        Spliterator.OfInt split = text.codePoints().spliterator().trySplit();
        return split == null ? 0 : split.estimateSize();
    }

    void invalid(String text) {
        // :: error: (dereference.of.nullable)
        text.chars().spliterator().trySplit().estimateSize();
        // :: error: (dereference.of.nullable)
        text.codePoints().spliterator().trySplit().estimateSize();
        // :: error: (dereference.of.nullable)
        text.lines().spliterator().trySplit().estimateSize();
        // :: error: (argument.type.incompatible)
        text.replace((CharSequence) null, "replacement");
    }
}
