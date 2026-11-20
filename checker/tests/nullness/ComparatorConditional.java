// @below-java17-jdk-skip-test
// JDK 17 study regression for conditional expressions with wildcard comparator inputs.
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.Comparator;

@AnnotatedFor("nullness")
class ComparatorConditional<T extends @Nullable Object> implements Comparator<T> {
    private final @Nullable Comparator<@NonNull T> real;

    @SuppressWarnings("unchecked")
    ComparatorConditional(@Nullable Comparator<? super @NonNull T> real) {
        this.real = (Comparator<@NonNull T>) real;
    }

    public int compare(T a, T b) {
        if (a == null) return b == null ? 0 : -1;
        if (b == null) return 1;
        return real == null ? 0 : real.compare(a, b);
    }

    public Comparator<T> thenComparing(Comparator<? super T> other) {
        return new ComparatorConditional<>(real == null ? other : real.thenComparing(other));
    }

    Comparator<T> separateReturns(Comparator<? super T> other) {
        if (real == null) return new ComparatorConditional<>(other);
        return new ComparatorConditional<>(real.thenComparing(other));
    }

    static void consumers(Comparator<String> strict, Comparator<@Nullable String> tolerant) {
        new ComparatorConditional<@Nullable String>(strict).compare(null, "a");
        new ComparatorConditional<@Nullable String>(strict)
                .thenComparing(tolerant)
                .compare(null, "a");
        new ComparatorConditional<String>(strict).separateReturns(strict).compare("a", "b");
        // :: error: (argument.type.incompatible)
        new ComparatorConditional<String>(strict).compare(null, "a");
        // :: error: (argument.type.incompatible)
        new ComparatorConditional<@Nullable String>(strict).thenComparing(strict);
    }
}
