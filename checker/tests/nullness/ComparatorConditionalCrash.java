// @skip-test Reproduces the open StructuralEqualityComparer NULL/TYPEVAR crash.
// JDK 17 study case NULL-0045; not counted as a passing regression test.
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.Comparator;

@AnnotatedFor("nullness")
class ComparatorConditionalCrash<T extends @Nullable Object> implements Comparator<@Nullable T> {
    private final @Nullable Comparator<T> real;

    @SuppressWarnings("unchecked")
    ComparatorConditionalCrash(@Nullable Comparator<? super T> real) {
        this.real = (Comparator<T>) real;
    }

    public int compare(@Nullable T a, @Nullable T b) {
        if (a == null) return b == null ? 0 : -1;
        if (b == null) return 1;
        return real == null ? 0 : real.compare(a, b);
    }

    public Comparator<@Nullable T> thenComparing(Comparator<? super @Nullable T> other) {
        return new ComparatorConditionalCrash<>(real == null ? other : real.thenComparing(other));
    }
}
