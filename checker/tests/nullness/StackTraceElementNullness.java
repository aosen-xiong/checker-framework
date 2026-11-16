// @below-java9-jdk-skip-test
// Nullness contracts documented by java.lang.StackTraceElement.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class StackTraceElementNullness {
    StackTraceElement valid(
            @Nullable String loader,
            @Nullable String module,
            @Nullable String version,
            @Nullable String file) {
        new StackTraceElement("Example", "run", null, 1);
        return new StackTraceElement(loader, module, version, "Example", "run", file, 1);
    }

    int guarded(StackTraceElement element) {
        String loader = element.getClassLoaderName();
        String module = element.getModuleName();
        String version = element.getModuleVersion();
        String file = element.getFileName();
        return (loader == null ? 0 : loader.length())
                + (module == null ? 0 : module.length())
                + (version == null ? 0 : version.length())
                + (file == null ? 0 : file.length())
                + element.getClassName().length()
                + element.getMethodName().length()
                + element.toString().length();
    }

    void invalidNames() {
        // :: error: (argument.type.incompatible)
        new StackTraceElement(null, "run", null, 1);
        // :: error: (argument.type.incompatible)
        new StackTraceElement("Example", null, null, 1);
        // :: error: (argument.type.incompatible)
        new StackTraceElement(null, null, null, null, "run", null, 1);
        // :: error: (argument.type.incompatible)
        new StackTraceElement(null, null, null, "Example", null, null, 1);
    }

    void unguarded(StackTraceElement element) {
        // :: error: (dereference.of.nullable)
        element.getClassLoaderName().length();
        // :: error: (dereference.of.nullable)
        element.getModuleName().length();
        // :: error: (dereference.of.nullable)
        element.getModuleVersion().length();
        // :: error: (dereference.of.nullable)
        element.getFileName().length();
    }
}
