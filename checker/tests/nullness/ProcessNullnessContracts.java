// @below-java17-jdk-skip-test
import org.checkerframework.framework.qual.AnnotatedFor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/** Compile-only consumers of the pinned process API documentation. */
@AnnotatedFor("nullness")
class ProcessNullnessContracts {
    void accepted(
            Process process,
            ProcessBuilder builder,
            ProcessHandle handle,
            ProcessHandle.Info info,
            Charset charset,
            File file)
            throws IOException {
        builder.directory(null).command("echo").environment().size();
        builder.redirectInput(ProcessBuilder.Redirect.from(file));
        builder.redirectOutput(ProcessBuilder.Redirect.to(file));
        builder.redirectError(ProcessBuilder.Redirect.appendTo(file));
        builder.redirectInput().type().name();
        builder.redirectOutput().equals(null);
        builder.redirectError().hashCode();
        builder.start().getInputStream().read();
        ProcessBuilder.startPipeline(List.of(builder)).get(0).getOutputStream().flush();
        process.getInputStream().read();
        process.getErrorStream().read();
        process.getOutputStream().flush();
        process.inputReader(charset).readLine();
        process.errorReader(charset).readLine();
        process.outputWriter(charset).flush();
        process.inputReader().readLine();
        process.errorReader().readLine();
        process.outputWriter().flush();
        process.onExit().thenAccept(p -> p.pid());
        process.toHandle().pid();
        process.info().command().ifPresent(s -> s.length());
        process.children().forEach(p -> p.pid());
        process.descendants().forEach(p -> p.pid());
        handle.equals(null);
        ProcessHandle.of(1).ifPresent(p -> p.pid());
        ProcessHandle.current().pid();
        handle.parent().ifPresent(p -> p.pid());
        handle.onExit().thenAccept(p -> p.pid());
        handle.children().forEach(p -> p.pid());
        handle.descendants().forEach(p -> p.pid());
        ProcessHandle.allProcesses().forEach(p -> p.pid());
        info.command().ifPresent(s -> s.length());
        info.commandLine().ifPresent(s -> s.length());
        info.arguments().ifPresent(a -> a[0].length());
        info.startInstant().ifPresent(t -> t.getEpochSecond());
        info.totalCpuDuration().ifPresent(d -> d.getSeconds());
        info.user().ifPresent(s -> s.length());
        Runtime.getRuntime().exec(new String[] {"echo"}, null, null);
    }

    void rejected(Process process, ProcessBuilder builder, ProcessHandle handle)
            throws IOException, InterruptedException {
        // :: error: (argument.type.incompatible)
        new ProcessBuilder((String[]) null);
        // :: error: (argument.type.incompatible)
        new ProcessBuilder((List<String>) null);
        // :: error: (argument.type.incompatible)
        builder.command((String[]) null);
        // :: error: (argument.type.incompatible)
        builder.command((List<String>) null);
        // :: error: (argument.type.incompatible)
        builder.redirectInput((ProcessBuilder.Redirect) null);
        // :: error: (argument.type.incompatible)
        builder.redirectOutput((ProcessBuilder.Redirect) null);
        // :: error: (argument.type.incompatible)
        builder.redirectError((ProcessBuilder.Redirect) null);
        // :: error: (argument.type.incompatible)
        ProcessBuilder.Redirect.from(null);
        // :: error: (argument.type.incompatible)
        ProcessBuilder.Redirect.to(null);
        // :: error: (argument.type.incompatible)
        ProcessBuilder.Redirect.appendTo(null);
        // :: error: (dereference.of.nullable)
        builder.directory().getPath();
        // :: error: (dereference.of.nullable)
        builder.redirectInput().file().getPath();
        // :: error: (argument.type.incompatible)
        ProcessBuilder.startPipeline(null);
        // :: error: (argument.type.incompatible)
        process.inputReader(null);
        // :: error: (argument.type.incompatible)
        process.errorReader(null);
        // :: error: (argument.type.incompatible)
        process.outputWriter(null);
        // :: error: (argument.type.incompatible)
        process.waitFor(1, null);
        // :: error: (argument.type.incompatible)
        handle.compareTo(null);
        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().addShutdownHook(null);
        // :: error: (argument.type.incompatible)
        Runtime.getRuntime().removeShutdownHook(null);
    }
}
