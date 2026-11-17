// @below-java17-jdk-skip-test
// Ignored lookup arguments, generic callback results, and newer text APIs.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;

@AnnotatedFor("nullness")
class TextModernNullness {
    int valid(
            String text,
            MethodHandles.@Nullable Lookup lookup,
            Function<String, @Nullable String> function) {
        String resolved = text.resolveConstantDesc(lookup);
        String transformed = text.transform(function);
        if (transformed != null) {
            transformed.length();
        }
        return resolved.length()
                + text.formatted((Object) null).length()
                + text.strip().length()
                + text.repeat(2).length()
                + text.lines().mapToInt(String::length).sum();
    }

    void invalid(
            String text,
            StringBuilder builder,
            StringBuffer buffer,
            Function<String, @Nullable String> function) {
        // :: error: (dereference.of.nullable)
        text.transform(function).length();
        // :: error: (argument.type.incompatible)
        text.transform(null);
        // :: error: (argument.type.incompatible)
        builder.compareTo(null);
        // :: error: (argument.type.incompatible)
        buffer.compareTo(null);
    }
}
