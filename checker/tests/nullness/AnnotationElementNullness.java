// @below-java17-jdk-skip-test
// Annotation elements describe nonnull strings and nonnull arrays of nonnull strings.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class AnnotationElementNullness {
    int elements(Deprecated deprecated, SuppressWarnings suppressed) {
        int total = deprecated.since().length();
        for (String warning : suppressed.value()) {
            total += warning.length();
        }
        return total;
    }

    void invalid(@Nullable Deprecated deprecated, @Nullable SuppressWarnings suppressed) {
        // :: error: (dereference.of.nullable)
        deprecated.since();
        // :: error: (dereference.of.nullable)
        suppressed.value();
    }
}
