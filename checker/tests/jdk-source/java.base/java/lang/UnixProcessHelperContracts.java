package java.lang;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.io.IOException;
import java.util.Map;

/** Compile-only controls for Unix sources; no processes or shutdown handlers are executed. */
@AnnotatedFor("nullness")
class UnixProcessHelperContracts {
    void optionalDefaults(@Nullable Map<String, String> environment, @Nullable String directory)
            throws IOException {
        ProcessImpl.start(new String[] {"command"}, environment, directory, null, false).pid();
        ProcessEnvironment.toEnvironmentBlock(null, new int[1]);
        ProcessEnvironment.toEnvironmentBlock(environment, new int[1]);
        ProcessEnvironment.environment().entrySet().iterator().next().getKey().length();
        ProcessEnvironment.getenv().entrySet().iterator().next().getValue().length();
        ProcessEnvironment.emptyEnvironment(0).size();
        ProcessImpl.newFileDescriptor(-1).toString();
        Terminator.setup();
        Terminator.teardown();
    }

    @Nullable String missingVariable(String name) {
        return ProcessEnvironment.getenv(name);
    }
}
