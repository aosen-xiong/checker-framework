package java.lang;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

/** CF-owned compile-only callers of Runtime's bootstrap-loader path. */
@AnnotatedFor("nullness")
class SystemRuntimeHelperContracts {
    void require(Object value) {}

    void versionCondition(Runtime.Version version, @Nullable Object value) {
        if (version.equalsIgnoreOptional(value)) require(value);
    }

    void nativeLoad(Runtime runtime) {
        runtime.load0(null, "/example/library");
        runtime.loadLibrary0(null, "example");
    }
}
