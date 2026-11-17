package java.util;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

/** Valid callers of the JDK 17 comparison implementation's refined contracts. */
@AnnotatedFor("nullness")
class ComparisonHelperContracts {
    void nonnullDelegates(Comparator<String> strict) {
        Comparators.NullComparator<@Nullable String> wrapper =
                new Comparators.NullComparator<>(true, strict);
        wrapper.compare(null, "a");
        wrapper.thenComparing(strict).compare(null, "a");
        wrapper.reversed().compare(null, null);
        Comparators.NullComparator<@Nullable String> absent =
                new Comparators.NullComparator<>(false, null);
        absent.thenComparing(strict).compare(null, null);
    }
}
