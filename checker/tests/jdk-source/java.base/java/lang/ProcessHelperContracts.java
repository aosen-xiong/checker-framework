package java.lang;

import org.checkerframework.framework.qual.AnnotatedFor;

/** Package-private positive controls, compiled with the source gate and never executed. */
@AnnotatedFor("nullness")
class ProcessHelperContracts {
    void optionalState(ProcessBuilder builder, ProcessHandleImpl.Info info) {
        builder.environment(null).directory(null);
        info.command = null;
        info.commandLine = null;
        info.arguments = null;
        info.user = null;
        info.command().ifPresent(s -> s.length());
        info.arguments().ifPresent(a -> a[0].length());
        ProcessHandleImpl.get(1).ifPresent(h -> h.pid());
        ProcessHandleImpl.completion(1, false).thenAccept(value -> value.intValue());
        new ProcessBuilder.RedirectPipeImpl().equals(null);
        ProcessBuilder.NullInputStream.INSTANCE.toString();
        ProcessBuilder.NullOutputStream.INSTANCE.toString();
    }
}
