// @below-java17-jdk-skip-test
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

/** Preserve the null initial value and type-argument contract; no thread is run. */
@AnnotatedFor("nullness")
class ThreadLocalNullnessContracts {
    void nullableValues() {
        ThreadLocal<@Nullable String> local = new ThreadLocal<>();
        local.set(null);
        local.remove();
        // :: error: (dereference.of.nullable)
        local.get().length();
        InheritableThreadLocal<@Nullable String> inherited = new InheritableThreadLocal<>();
        inherited.set(null);
        // :: error: (dereference.of.nullable)
        inherited.get().length();
        ThreadLocal<@Nullable String> supplied = ThreadLocal.withInitial(() -> null);
        supplied.set(null);
        // :: error: (dereference.of.nullable)
        supplied.get().length();
        ThreadLocal.withInitial(() -> "ready").get().length();
        // :: error: (argument.type.incompatible)
        ThreadLocal.<@Nullable String>withInitial(null);
        // :: error: (type.argument.type.incompatible)
        new ThreadLocal<String>();
        // :: error: (type.argument.type.incompatible)
        new InheritableThreadLocal<String>();
    }

    // The existing CF model requires this exception for a sound non-null initialValue override.
    @SuppressWarnings("nullness:type.argument.type.incompatible")
    static final class Initialized extends ThreadLocal<String> {
        @Override
        protected String initialValue() {
            return "ready";
        }
    }

    static final class Inherited extends InheritableThreadLocal<@Nullable String> {
        @Override
        protected @Nullable String childValue(@Nullable String parent) {
            return parent;
        }

        @Nullable String child(@Nullable String parent) {
            return childValue(parent);
        }
    }

    void initialized(Initialized local, Inherited inherited) {
        local.get().length();
        local.set("ready");
        // :: error: (argument.type.incompatible)
        local.set(null);
        // :: error: (dereference.of.nullable)
        inherited.child(null).length();
    }
}
