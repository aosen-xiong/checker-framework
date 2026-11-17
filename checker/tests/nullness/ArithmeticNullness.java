// Arithmetic APIs take primitives: nullable boxed values require a guard before unboxing.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class ArithmeticNullness {
    double primitives(double value, int count) {
        return Math.abs(value)
                + Math.addExact(count, 1)
                + Math.random()
                + StrictMath.exp(value)
                + StrictMath.cbrt(value)
                + StrictMath.pow(value, 2.0)
                + StrictMath.hypot(value, 1.0)
                + StrictMath.random();
    }

    double guarded(@Nullable Double value) {
        if (value != null) {
            return StrictMath.sqrt(value) + Math.abs(value);
        }
        return 0.0;
    }

    void invalid(
            @Nullable Integer count,
            @Nullable Long offset,
            @Nullable Double value,
            @Nullable Float fraction) {
        // :: error: (unboxing.of.nullable)
        Math.abs(count);
        // :: error: (unboxing.of.nullable)
        Math.addExact(offset, 1L);
        // :: error: (unboxing.of.nullable)
        StrictMath.sqrt(value);
        // :: error: (unboxing.of.nullable)
        StrictMath.abs(fraction);
    }
}
