// Documentation-backed nullable exception causes and diagnostic messages.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class ExceptionCauseNullness {
    void constructors(
            @Nullable String message, @Nullable Throwable cause, @Nullable Object detail) {
        new ClassNotFoundException();
        new ClassNotFoundException(message);
        new ClassNotFoundException(message, cause);
        new ExceptionInInitializerError();
        new ExceptionInInitializerError(message);
        new ExceptionInInitializerError(cause);
        new ReflectiveOperationException();
        new ReflectiveOperationException(message);
        new ReflectiveOperationException(cause);
        new ReflectiveOperationException(message, cause);
        new LinkageError();
        new LinkageError(message);
        new LinkageError(message, cause);
        new AssertionError(detail);
        new AssertionError(message, cause);
        new TypeNotPresentException("example.Missing", cause);
        new EnumConstantNotPresentException(Thread.State.class, "MISSING");
        new NullPointerException();
        new NullPointerException(message);
    }

    int guarded(
            ClassNotFoundException loading,
            ExceptionInInitializerError initialization,
            NullPointerException dereference) {
        Throwable loadingCause = loading.getException();
        Throwable initializationCause = initialization.getException();
        String message = dereference.getMessage();
        return (loadingCause == null ? 0 : loadingCause.toString().length())
                + (initializationCause == null ? 0 : initializationCause.toString().length())
                + (message == null ? 0 : message.length());
    }

    void unguarded(
            ClassNotFoundException loading,
            ExceptionInInitializerError initialization,
            NullPointerException dereference) {
        // :: error: (dereference.of.nullable)
        loading.getException().toString();
        // :: error: (dereference.of.nullable)
        initialization.getException().toString();
        // :: error: (dereference.of.nullable)
        dereference.getMessage().length();
    }

    static class MissingCause extends ClassNotFoundException {
        @Override
        public @Nullable Throwable getException() {
            return null;
        }
    }
}
