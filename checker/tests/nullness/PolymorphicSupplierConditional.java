import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.function.Supplier;

@AnnotatedFor("nullness")
class PolymorphicSupplierConditional<T> {
    @PolyNull T exact(Supplier<@PolyNull T> supplier) {
        return supplier.get();
    }

    @PolyNull T wildcard(Supplier<? extends @PolyNull T> supplier) {
        return supplier.get();
    }

    @PolyNull T separateReturns(@Nullable T value, Supplier<? extends @PolyNull T> supplier) {
        if (value != null) return value;
        return supplier.get();
    }

    @PolyNull T exactChoose(@Nullable T value, Supplier<@PolyNull T> supplier) {
        return value != null ? value : supplier.get();
    }

    // CF conditional/wildcard limitation; equivalent forms above pass unsuppressed.
    @SuppressWarnings("nullness:conditional.type.incompatible")
    @PolyNull T choose(@Nullable T value, Supplier<? extends @PolyNull T> supplier) {
        return value != null ? value : supplier.get();
    }

    static void callers(
            PolymorphicSupplierConditional<String> probe,
            Supplier<String> nonnull,
            Supplier<@Nullable String> nullable) {
        probe.choose(null, nonnull).length();
        // :: error: (dereference.of.nullable)
        probe.choose(null, nullable).length();
    }
}
