// @below-java17-jdk-skip-test
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/** Compile-only consumers derived from StackWalker and StackFrame Javadoc. */
@AnnotatedFor("nullness")
class StackWalkingNullnessContracts {
    // JDK defaulting gap: KeyForBottom reaches explicit callback lower bounds.
    @SuppressWarnings("keyfor:type.invalid.annotations.on.location")
    void accepted(StackWalker walker, StackWalker.StackFrame frame) {
        StackWalker.getInstance().forEach(f -> f.getClassName().length());
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .getCallerClass()
                .getName();
        StackWalker.getInstance(Set.of(), 10).walk(s -> s.count()).longValue();
        walker.walk(s -> "result").length();
        walker.forEach(f -> f.getMethodName().length());
        frame.getClassName().length();
        frame.getMethodName().length();
        frame.getDeclaringClass().getName();
        frame.getMethodType().returnType().getName();
        frame.getDescriptor().length();
        frame.toStackTraceElement().getMethodName().length();
        String file = frame.getFileName();
        if (file != null) {
            file.length();
        }
    }

    // JDK defaulting gap: KeyForBottom reaches explicit callback lower bounds.
    @SuppressWarnings("keyfor:type.arguments.not.inferred")
    @Nullable String nullableResult(
            StackWalker walker,
            Function<Stream<StackWalker.StackFrame>, @Nullable String> function) {
        return walker.walk(function);
    }

    // JDK defaulting gap: KeyForBottom reaches explicit callback lower bounds.
    @SuppressWarnings("keyfor:type.invalid.annotations.on.location")
    @Nullable String nullResult(StackWalker walker) {
        return walker.walk(s -> null);
    }

    // JDK defaulting gap: KeyForBottom reaches explicit callback lower bounds.
    @SuppressWarnings("keyfor:type.invalid.annotations.on.location")
    void rejected(
            StackWalker walker,
            StackWalker.StackFrame frame,
            Set<StackWalker.@Nullable Option> invalidOptions) {
        // :: error: (argument.type.incompatible)
        StackWalker.getInstance((StackWalker.Option) null);
        // :: error: (argument.type.incompatible)
        StackWalker.getInstance((Set<StackWalker.Option>) null);
        // :: error: (argument.type.incompatible)
        StackWalker.getInstance(null, 1);
        // :: error: (argument.type.incompatible)
        StackWalker.getInstance(invalidOptions);
        // :: error: (argument.type.incompatible)
        walker.walk(null);
        // :: error: (argument.type.incompatible)
        walker.forEach(null);
        // :: error: (dereference.of.nullable)
        frame.getFileName().length();
        // :: error: (dereference.of.nullable)
        frame.toStackTraceElement().getFileName().length();
        // :: error: (dereference.of.nullable)
        walker.<@Nullable String>walk(s -> null).length();
    }
}
