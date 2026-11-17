// @below-java17-jdk-skip-test
// Checks the source contracts of the JDK 17 comparator study.
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

@AnnotatedFor("nullness")
class ComparatorNullness {
    void domains(Comparator<String> strict, Comparator<@Nullable String> tolerant) {
        strict.compare("a", "b");
        tolerant.compare(null, "b");
        tolerant.compare("a", null);
        tolerant.equals(null);
        tolerant.reversed().compare(null, "b");
        tolerant.thenComparing(tolerant).compare(null, null);
        // :: error: (argument.type.incompatible)
        strict.compare(null, "b");
        // :: error: (argument.type.incompatible)
        strict.reversed().compare("a", null);
        // :: error: (argument.type.incompatible)
        strict.thenComparing((Comparator<String>) null);
        // :: error: (argument.type.incompatible)
        tolerant.thenComparing(strict);
    }

    void nullOrdering(Comparator<String> strict, Comparator<@Nullable String> tolerant) {
        Comparator<@Nullable String> first = Comparator.nullsFirst(strict);
        Comparator<@Nullable String> last = Comparator.nullsLast(strict);
        first.compare(null, "a");
        last.compare("a", null);
        first.reversed().compare(null, null);
        first.thenComparing(tolerant).compare(null, "a");
        Comparator.<String>nullsFirst(null).compare(null, "a");
        Comparator.<String>nullsLast(null).reversed().compare(null, null);
        Comparator.nullsFirst(tolerant).compare(null, "a");
        Comparator.nullsLast(tolerant).compare("a", null);
        // :: error: (argument.type.incompatible)
        first.thenComparing(strict);
        // :: error: (argument.type.incompatible)
        Comparator.<String>naturalOrder().compare(null, "a");
        // :: error: (argument.type.incompatible)
        Comparator.<String>reverseOrder().compare("a", null);
    }

    void extractedKeys(
            Function<String, String> key,
            Function<String, @Nullable String> nullableKey,
            Function<@Nullable String, String> nullableInput,
            Comparator<String> strict,
            Comparator<@Nullable String> tolerant) {
        Comparator<String> byKey = Comparator.comparing(key);
        Comparator<String> byNullableKey = Comparator.comparing(nullableKey, tolerant);
        Comparator<@Nullable String> nullableDomain = Comparator.comparing(nullableInput);
        byKey.compare("a", "b");
        byNullableKey.compare("a", "b");
        nullableDomain.compare(null, "a");
        strict.thenComparing(key).compare("a", "b");
        strict.thenComparing(nullableKey, tolerant).compare("a", "b");
        tolerant.thenComparing(nullableInput).compare(null, "b");
        // :: error: (argument.type.incompatible)
        byKey.compare(null, "b");
        // :: error: (type.arguments.not.inferred)
        Comparator<String> badNatural = Comparator.comparing(nullableKey);
        // :: error: (type.arguments.not.inferred)
        Comparator<String> badDelegate = Comparator.comparing(nullableKey, strict);
        // :: error: (type.arguments.not.inferred)
        strict.thenComparing(nullableKey);
        // :: error: (type.arguments.not.inferred)
        strict.thenComparing(nullableKey, strict);
        // :: error: (argument.type.incompatible)
        Comparator.comparing((Function<String, String>) null);
        // :: error: (argument.type.incompatible)
        Comparator.comparing(key, null);
        // :: error: (argument.type.incompatible)
        strict.thenComparing((Function<String, String>) null);
        // :: error: (argument.type.incompatible)
        strict.thenComparing(key, null);
    }

    void primitiveKeys(
            Comparator<String> strict,
            ToIntFunction<String> ints,
            ToLongFunction<String> longs,
            ToDoubleFunction<String> doubles,
            ToIntFunction<@Nullable String> nullableInput) {
        Comparator.comparingInt(ints).compare("a", "b");
        Comparator.comparingLong(longs).compare("a", "b");
        Comparator.comparingDouble(doubles).compare("a", "b");
        Comparator.comparingInt(nullableInput).compare(null, "b");
        strict.thenComparingInt(ints).compare("a", "b");
        strict.thenComparingLong(longs).compare("a", "b");
        strict.thenComparingDouble(doubles).compare("a", "b");
        // :: error: (argument.type.incompatible)
        Comparator.comparingInt(ints).compare(null, "b");
        // :: error: (argument.type.incompatible)
        Comparator.comparingInt((ToIntFunction<String>) null);
        // :: error: (argument.type.incompatible)
        Comparator.comparingLong((ToLongFunction<String>) null);
        // :: error: (argument.type.incompatible)
        Comparator.comparingDouble((ToDoubleFunction<String>) null);
        // :: error: (argument.type.incompatible)
        strict.thenComparingInt(null);
        // :: error: (argument.type.incompatible)
        strict.thenComparingLong(null);
        // :: error: (argument.type.incompatible)
        strict.thenComparingDouble(null);
    }
}
