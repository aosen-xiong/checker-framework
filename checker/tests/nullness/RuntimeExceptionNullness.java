// @below-java17-jdk-skip-test
// Existing nullable message/cause contracts across runtime and bounds exceptions.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class RuntimeExceptionNullness {
    RuntimeException[] messages(@Nullable String message) {
        return new RuntimeException[] {
            new ArithmeticException(message),
            new ArrayStoreException(message),
            new ClassCastException(message),
            new IllegalArgumentException(message),
            new IllegalCallerException(message),
            new IllegalMonitorStateException(message),
            new IllegalStateException(message),
            new IllegalThreadStateException(message),
            new LayerInstantiationException(message),
            new NegativeArraySizeException(message),
            new SecurityException(message),
            new UnsupportedOperationException(message),
            new NumberFormatException(message),
            new IndexOutOfBoundsException(message),
            new ArrayIndexOutOfBoundsException(message),
            new StringIndexOutOfBoundsException(message)
        };
    }

    RuntimeException[] causes(@Nullable String message, @Nullable Throwable cause) {
        return new RuntimeException[] {
            new IllegalArgumentException(message, cause),
            new IllegalArgumentException(cause),
            new IllegalCallerException(message, cause),
            new IllegalCallerException(cause),
            new IllegalStateException(message, cause),
            new IllegalStateException(cause),
            new LayerInstantiationException(message, cause),
            new LayerInstantiationException(cause),
            new SecurityException(message, cause),
            new SecurityException(cause),
            new UnsupportedOperationException(message, cause),
            new UnsupportedOperationException(cause)
        };
    }

    int guarded(IllegalCallerException caller, LayerInstantiationException layer) {
        String message = caller.getMessage();
        Throwable cause = layer.getCause();
        return (message == null ? 0 : message.length())
                + (cause == null ? 0 : cause.toString().length());
    }

    IndexOutOfBoundsException[] bounds(int index, long largeIndex) {
        return new IndexOutOfBoundsException[] {
            new IndexOutOfBoundsException(index),
            new IndexOutOfBoundsException(largeIndex),
            new ArrayIndexOutOfBoundsException(index),
            new StringIndexOutOfBoundsException(index)
        };
    }

    void invalid(
            IllegalCallerException caller,
            LayerInstantiationException layer,
            NumberFormatException number,
            IndexOutOfBoundsException bounds) {
        // :: error: (dereference.of.nullable)
        caller.getMessage().length();
        // :: error: (dereference.of.nullable)
        layer.getCause().toString();
        // :: error: (dereference.of.nullable)
        number.getMessage().length();
        // :: error: (dereference.of.nullable)
        bounds.getCause().toString();
        // :: error: (argument.type.incompatible)
        caller.addSuppressed(null);
    }
}
