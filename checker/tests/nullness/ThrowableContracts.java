// Contracts documented by Throwable and its immediate exception/error subclasses.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class ThrowableContracts {
    void constructors(@Nullable String message, @Nullable Throwable cause) {
        new Throwable();
        new Throwable(message);
        new Throwable(cause);
        new Throwable(message, cause);
        new Exception();
        new Exception(message);
        new Exception(cause);
        new Exception(message, cause);
        new RuntimeException();
        new RuntimeException(message);
        new RuntimeException(cause);
        new RuntimeException(message, cause);
        new Error();
        new Error(message);
        new Error(cause);
        new Error(message, cause);
        new Throwable().initCause(null).toString().length();
    }

    int guarded(Throwable throwable) {
        String message = throwable.getMessage();
        String localized = throwable.getLocalizedMessage();
        Throwable cause = throwable.getCause();
        return (message == null ? 0 : message.length())
                + (localized == null ? 0 : localized.length())
                + (cause == null ? 0 : cause.toString().length())
                + throwable.fillInStackTrace().toString().length();
    }

    void arrays(Throwable throwable, StackTraceElement frame, Throwable suppressed) {
        throwable.setStackTrace(new StackTraceElement[] {frame});
        throwable.addSuppressed(suppressed);
        for (StackTraceElement element : throwable.getStackTrace()) {
            element.getClassName().length();
        }
        for (Throwable element : throwable.getSuppressed()) {
            element.toString().length();
        }
    }

    void invalid(Throwable throwable) {
        // :: error: (dereference.of.nullable)
        throwable.getMessage().length();
        // :: error: (dereference.of.nullable)
        throwable.getLocalizedMessage().length();
        // :: error: (dereference.of.nullable)
        throwable.getCause().toString();
        // :: error: (argument.type.incompatible)
        throwable.addSuppressed(null);
        // :: error: (argument.type.incompatible)
        throwable.setStackTrace(null);
        // :: error: (argument.type.incompatible)
        throwable.setStackTrace(new @Nullable StackTraceElement[] {null});
    }

    static class DisabledThrowable extends Throwable {
        DisabledThrowable() {
            super(null, null, false, false);
        }
    }

    static class DisabledException extends Exception {
        DisabledException() {
            super(null, null, false, false);
        }
    }

    static class DisabledRuntimeException extends RuntimeException {
        DisabledRuntimeException() {
            super(null, null, false, false);
        }
    }

    static class DisabledError extends Error {
        DisabledError() {
            super(null, null, false, false);
        }
    }

    int disabled() {
        return new DisabledThrowable().getStackTrace().length
                + new DisabledException().getSuppressed().length
                + new DisabledRuntimeException().getStackTrace().length
                + new DisabledError().getSuppressed().length;
    }

    static class InvalidStackTrace extends Throwable {
        @Override
        public StackTraceElement[] getStackTrace() {
            // :: error: (return.type.incompatible)
            return null;
        }
    }
}
