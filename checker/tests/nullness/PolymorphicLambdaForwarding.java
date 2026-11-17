// Suspected inference defect reproduced from WeakPairMap.computeIfAbsent.
// Direct forwarding preserves PolyNull; the equivalent lambda currently needs suppression.
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.function.Function;

@AnnotatedFor("nullness")
class PolymorphicLambdaForwarding {
    static <V extends @NonNull Object> @PolyNull V invoke(
            Function<String, ? extends @PolyNull V> function) {
        return function.apply("key");
    }

    static <V extends @NonNull Object> @PolyNull V direct(
            Function<String, ? extends @PolyNull V> function) {
        return invoke(function);
    }

    @SuppressWarnings(
            "nullness:return.type.incompatible") // CF loses PolyNull inside the forwarding lambda.
    static <V extends @NonNull Object> @PolyNull V relay(
            Function<String, ? extends @PolyNull V> function) {
        return invoke(key -> function.apply(key));
    }

    void callers(Function<String, String> nonnull, Function<String, @Nullable String> nullable) {
        direct(nonnull).length();
        relay(nonnull).length();
        // :: error: (dereference.of.nullable)
        direct(nullable).length();
        // :: error: (dereference.of.nullable)
        relay(nullable).length();
    }
}
