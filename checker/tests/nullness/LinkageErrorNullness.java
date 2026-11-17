// Message/cause inputs in the remaining linkage-error families.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class LinkageErrorNullness {
    LinkageError[] messages(@Nullable String message) {
        return new LinkageError[] {
            new AbstractMethodError(message),
            new BootstrapMethodError(message),
            new ClassCircularityError(message),
            new ClassFormatError(message),
            new IllegalAccessError(message),
            new IncompatibleClassChangeError(message),
            new InstantiationError(message),
            new NoClassDefFoundError(message),
            new NoSuchFieldError(message),
            new NoSuchMethodError(message),
            new UnsatisfiedLinkError(message),
            new UnsupportedClassVersionError(message),
            new VerifyError(message)
        };
    }

    BootstrapMethodError[] causes(@Nullable String message, @Nullable Throwable cause) {
        return new BootstrapMethodError[] {
            new BootstrapMethodError(message, cause), new BootstrapMethodError(cause)
        };
    }

    int guarded(BootstrapMethodError failure) {
        String message = failure.getMessage();
        Throwable cause = failure.getCause();
        return (message == null ? 0 : message.length())
                + (cause == null ? 0 : cause.toString().length());
    }

    void invalid(BootstrapMethodError bootstrap, IncompatibleClassChangeError incompatible) {
        // :: error: (dereference.of.nullable)
        bootstrap.getMessage().length();
        // :: error: (dereference.of.nullable)
        bootstrap.getCause().toString();
        // :: error: (dereference.of.nullable)
        incompatible.getMessage().length();
        // :: error: (argument.type.incompatible)
        bootstrap.addSuppressed(null);
    }
}
