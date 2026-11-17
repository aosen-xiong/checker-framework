// Existing optional message/cause contracts in the VM error hierarchy.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class VmErrorNullness {
    static class Failure extends VirtualMachineError {
        Failure() {
            super();
        }

        Failure(@Nullable String message) {
            super(message);
        }

        Failure(@Nullable String message, @Nullable Throwable cause) {
            super(message, cause);
        }

        Failure(@Nullable Throwable cause) {
            super(cause);
        }
    }

    VirtualMachineError[] messagesAndCauses(@Nullable String message, @Nullable Throwable cause) {
        return new VirtualMachineError[] {
            new Failure(),
            new Failure(message),
            new Failure(message, cause),
            new Failure(cause),
            new InternalError(message),
            new InternalError(message, cause),
            new InternalError(cause),
            new OutOfMemoryError(message),
            new StackOverflowError(message),
            new UnknownError(message)
        };
    }

    int guarded(VirtualMachineError failure) {
        String message = failure.getMessage();
        Throwable cause = failure.getCause();
        return (message == null ? 0 : message.length())
                + (cause == null ? 0 : cause.toString().length());
    }

    void invalid(InternalError internal, OutOfMemoryError memory, StackOverflowError stack) {
        // :: error: (dereference.of.nullable)
        internal.getCause().toString();
        // :: error: (dereference.of.nullable)
        memory.getMessage().length();
        // :: error: (dereference.of.nullable)
        stack.getMessage().length();
    }
}
