// @below-java17-jdk-skip-test
// Runtime Javadoc: optional environment/directory, non-null commands, version equality.
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.io.IOException;

@AnnotatedFor("nullness")
class RuntimeVersionNullness {
    void require(Object value) {}

    void valid(Runtime runtime, Runtime.Version version, @Nullable Object other)
            throws IOException {
        require(Runtime.getRuntime());
        require(Runtime.version());
        require(runtime.exec("command", null, null));
        require(runtime.exec(new String[] {"command"}, null, null));
        require(Runtime.Version.parse("17"));
        require(version.version());
        for (Integer component : version.version()) require(component);
        require(version.pre());
        require(version.build());
        require(version.optional());
        require(version.toString());
        version.equals(null);
        version.equalsIgnoreOptional(null);
        if (version.equalsIgnoreOptional(other)) require(other);
        if (version.equals(other)) require(other);
    }

    void invalid(Runtime runtime, Runtime.Version version, @Nullable String[] elements)
            throws IOException {
        // :: error: (argument.type.incompatible)
        runtime.exec((String) null);
        // :: error: (argument.type.incompatible)
        runtime.exec((String[]) null);
        // :: error: (argument.type.incompatible)
        runtime.exec(elements);
        // :: error: (argument.type.incompatible)
        runtime.exec("command", elements);
        // :: error: (argument.type.incompatible)
        runtime.load(null);
        // :: error: (argument.type.incompatible)
        Runtime.Version.parse(null);
        // :: error: (argument.type.incompatible)
        version.compareTo(null);
        // :: error: (argument.type.incompatible)
        version.compareToIgnoreOptional(null);
    }
}
