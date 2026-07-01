package checker_reconcile.repair;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Runs javac/Checker Framework validation for a candidate patch. */
public final class Validation {
    private final boolean echoOutput;
    private final List<String> extraOptions;
    private final List<Path> extraClasspath;

    public Validation() {
        this(true);
    }

    public Validation(boolean echoOutput) {
        this(echoOutput, Collections.emptyList(), Collections.emptyList());
    }

    public Validation(boolean echoOutput, List<String> extraOptions, List<Path> extraClasspath) {
        this.echoOutput = echoOutput;
        this.extraOptions = Collections.unmodifiableList(new ArrayList<>(extraOptions));
        this.extraClasspath = Collections.unmodifiableList(new ArrayList<>(extraClasspath));
    }

    public int validate(String javac, Path checkerJar, Path source)
            throws IOException, InterruptedException {
        return validateDetailed(javac, checkerJar, source).exitCode();
    }

    public Result validateDetailed(String javac, Path checkerJar, Path source)
            throws IOException, InterruptedException {
        return validateDetailed(javac, checkerJar, source, null);
    }

    public Result validateDetailed(String javac, Path checkerJar, Path source, Path traceOut)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(javac);
        addCheckerFrameworkJavacJvmOptions(command);
        command.add("-processor");
        command.add("org.checkerframework.checker.nullness.NullnessChecker");
        if (traceOut != null) {
            command.add("-AexportNullnessTrace=" + traceOut);
        }
        String classpath = classpath(checkerJar);
        if (!classpath.isEmpty()) {
            command.add("-cp");
            command.add(classpath);
        }
        if (checkerJar != null) {
            command.add("-processorpath");
            command.add(checkerJar.toString());
        }
        command.addAll(extraOptions);
        command.add(source.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] outputBytes = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        String output = new String(outputBytes, StandardCharsets.UTF_8);
        if (echoOutput && !output.isEmpty()) {
            System.err.print(output);
        }
        return new Result(exitCode, diagnosticCount(output));
    }

    private String classpath(Path checkerJar) {
        List<String> entries = new ArrayList<>();
        if (checkerJar != null) {
            entries.add(checkerJar.toString());
        }
        for (Path path : extraClasspath) {
            entries.add(path.toString());
        }
        return String.join(File.pathSeparator, entries);
    }

    private void addCheckerFrameworkJavacJvmOptions(List<String> command) {
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED");
        command.add("-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
        command.add("-J--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED");
    }

    private int diagnosticCount(String output) {
        int count = 0;
        for (String line : output.split("\\R")) {
            if (line.contains(": error:")) {
                count++;
            }
        }
        return count;
    }

    /** Result of one validation compilation. */
    public static final class Result {
        private final int exitCode;
        private final int diagnosticCount;

        public Result(int exitCode, int diagnosticCount) {
            this.exitCode = exitCode;
            this.diagnosticCount = diagnosticCount;
        }

        public int exitCode() {
            return exitCode;
        }

        public int diagnosticCount() {
            return diagnosticCount;
        }
    }
}
