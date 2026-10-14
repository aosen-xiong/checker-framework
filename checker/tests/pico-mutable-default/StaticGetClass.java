import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// x.getClass() has type Class<? extends erasure(x)>. The type argument's upper bound is @Readonly,
// not the @ReceiverDependentMutable declaration bound of Object, of arrays, or of an RDM class, so
// using the result in a static method is not a static @ReceiverDependentMutable.
@ReceiverDependentMutable class StaticGetClassRdm {}

class StaticGetClass {
    static void object(@Readonly Object o) {
        Class<?> c = o.getClass();
    }

    static void readonlyArray(Object @Readonly [] a) {
        Class<?> c = a.getClass();
    }

    static void immutableArray(@Immutable Object @Immutable [] a) {
        Class<?> c = a.getClass();
    }

    static void mutableArray(@Mutable Object @Mutable [] a) {
        Class<?> c = a.getClass();
    }

    static void rdmClass(@Readonly StaticGetClassRdm r) {
        Class<? extends @Readonly StaticGetClassRdm> c = r.getClass();
    }

    void instance(Object @Readonly [] a) {
        Class<? extends @Readonly Object> c = a.getClass();
    }

    // The shape of java.util.Arrays.copyOf(T[], int), with a readonly array in the Class bound.
    static <T, U> T[] copy(
            U @Readonly [] original, int n, Class<? extends T @Readonly []> newType) {
        return null;
    }

    @SuppressWarnings("unchecked")
    static <T> T[] copyOf(T @Readonly [] original, int n) {
        return (T[]) copy(original, n, original.getClass());
    }
}
