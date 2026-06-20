import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Issue949 {
    // :: warning: (monotonicnonnull.on.static.field)
    static @MonotonicNonNull Object staticField;

    @MonotonicNonNull Object instanceField;

    // :: warning: (monotonicnonnull.on.static.field)
    static @MonotonicNonNull Object @Nullable [] staticArrayWithMonotonicElements = null;
}
