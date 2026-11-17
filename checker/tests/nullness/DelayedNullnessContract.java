import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class DelayedNullnessContract {
    @EnsuresNonNullIf(expression = "#1", result = true)
    boolean present(@Nullable Object value) {
        return value != null;
    }

    int direct(@Nullable Object value) {
        if (!present(value)) return 0;
        return value.hashCode();
    }

    int delayed(@Nullable Object value) {
        boolean result = present(value);
        if (!result) return 0;
        // :: error: (dereference.of.nullable)
        return value.hashCode();
    }

    @EnsuresNonNullIf(expression = "#1", result = true)
    boolean delayedContract(@Nullable Object value) {
        boolean result = present(value);
        if (!result) return false;
        Object alias = value;
        // :: error: (contracts.conditional.postcondition.not.satisfied)
        // :: error: (dereference.of.nullable)
        return alias.hashCode() == 0;
    }
}
