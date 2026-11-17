// Existing optional messages in the checked/reflection exception subclasses.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class CheckedExceptionNullness {
    Exception[] messages(@Nullable String message) {
        return new Exception[] {
            new CloneNotSupportedException(message),
            new IllegalAccessException(message),
            new InstantiationException(message),
            new InterruptedException(message),
            new NoSuchFieldException(message),
            new NoSuchMethodException(message)
        };
    }

    int guarded(ReflectiveOperationException failure) {
        String message = failure.getMessage();
        Throwable cause = failure.getCause();
        return (message == null ? 0 : message.length())
                + (cause == null ? 0 : cause.toString().length());
    }

    void invalid(
            CloneNotSupportedException clone,
            IllegalAccessException access,
            InstantiationException instance,
            InterruptedException interrupted,
            NoSuchFieldException field,
            NoSuchMethodException method) {
        // :: error: (dereference.of.nullable)
        clone.getMessage().length();
        // :: error: (dereference.of.nullable)
        access.getMessage().length();
        // :: error: (dereference.of.nullable)
        instance.getCause().toString();
        // :: error: (dereference.of.nullable)
        interrupted.getMessage().length();
        // :: error: (dereference.of.nullable)
        field.getMessage().length();
        // :: error: (dereference.of.nullable)
        method.getCause().toString();
    }
}
