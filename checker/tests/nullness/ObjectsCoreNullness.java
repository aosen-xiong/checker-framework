// @below-java17-jdk-skip-test
// Objects source Javadoc: null-tolerant utilities and fallback result propagation.
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.Comparator;
import java.util.Objects;

@AnnotatedFor("nullness")
class ObjectsCoreNullness {
    void require(Object value) {}

    void valid(
            @Nullable String value,
            @Nullable String fallback,
            Comparator<String> strict,
            Comparator<@Nullable String> tolerant) {
        Objects.equals(value, null);
        Objects.deepEquals(null, value);
        Objects.hashCode(null);
        Objects.hash((Object[]) null);
        Objects.hash(value, null, "value");
        require(Objects.toString(value));
        require(Objects.toString(value, "fallback"));
        String result = Objects.toString(value, fallback);
        if (result != null) require(result);
        Objects.compare(null, null, strict);
        Objects.compare("a", "b", strict);
        Objects.compare(value, null, tolerant);
        if (Objects.nonNull(value)) require(value);
        if (!Objects.isNull(value)) require(value);
        require(Objects.requireNonNull("value", (String) null));
        require(Objects.requireNonNullElse(value, "fallback"));
        require(Objects.requireNonNullElseGet(value, () -> "fallback"));
    }

    void invalid(@Nullable String value, Comparator<String> strict) {
        // :: error: (dereference.of.nullable)
        Objects.toString(value, null).length();
        // :: error: (argument.type.incompatible)
        Objects.requireNonNull(null);
        // :: error: (argument.type.incompatible)
        Objects.requireNonNullElse(null, null);
        // :: error: (type.arguments.not.inferred)
        Objects.requireNonNullElseGet(null, () -> null);
        // :: error: (argument.type.incompatible)
        strict.compare(null, "value");
    }
}
