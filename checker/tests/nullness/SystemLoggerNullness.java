// @below-java17-jdk-skip-test
// Pinned System/Logger Javadoc: nullable messages and bundles, non-null levels.
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.io.IOException;
import java.util.ResourceBundle;
import java.util.function.Supplier;

@AnnotatedFor("nullness")
class SystemLoggerNullness {
    void require(Object value) {}

    void system() throws IOException {
        require(System.getProperties());
        require(System.lineSeparator());
        require(System.getenv());
        for (var entry : System.getenv().entrySet()) {
            require(entry.getKey());
            require(entry.getValue());
        }
        require(System.getProperty("key", "default"));
        String absent = System.getProperty("key", null);
        if (absent != null) require(absent);
        // Existing checker policy protects predefined properties despite the nullable API.
        // :: error: (clear.system.property)
        System.setProperties(null);
        System.identityHashCode(null);
        var console = System.console();
        if (console != null) require(console);
        var channel = System.inheritedChannel();
        if (channel != null) channel.close();
        require(System.getLogger("name"));
        require(System.mapLibraryName("name"));
    }

    void logger(
            System.Logger log,
            System.LoggerFinder finder,
            Module module,
            Supplier<@Nullable String> supplier) {
        require(log.getName());
        Supplier<String> nonnullSupplier = () -> "message";
        log.log(System.Logger.Level.INFO, nonnullSupplier);
        log.log(System.Logger.Level.INFO, (String) null);
        log.log(System.Logger.Level.INFO, (String) null, (Throwable) null);
        log.log(System.Logger.Level.INFO, supplier);
        log.log(System.Logger.Level.INFO, supplier, null);
        log.log(System.Logger.Level.INFO, (String) null, (Object[]) null);
        log.log(System.Logger.Level.INFO, "{0}", new @Nullable Object[] {null});
        log.log(System.Logger.Level.INFO, null, null, (Throwable) null);
        log.log(System.Logger.Level.INFO, (ResourceBundle) null, null, (Object[]) null);
        require(finder.getLocalizedLogger("name", null, module));
        require(finder.getLogger("name", module));
        require(System.LoggerFinder.getLoggerFinder());
    }

    void invalid(System.Logger log) throws IOException {
        // :: error: (dereference.of.nullable)
        System.console().flush();
        // :: error: (dereference.of.nullable)
        System.inheritedChannel().close();
        // :: error: (dereference.of.nullable)
        System.getProperty("key").length();
        // :: error: (dereference.of.nullable)
        System.getProperty("key", null).length();
        // :: error: (dereference.of.nullable)
        System.setProperty("key", "value").length();
        // :: error: (dereference.of.nullable)
        System.clearProperty("key").length();
        // :: error: (dereference.of.nullable)
        System.getenv("KEY").length();
        // :: error: (argument.type.incompatible)
        System.getProperty(null);
        // :: error: (argument.type.incompatible)
        System.setProperty("key", null);
        // :: error: (argument.type.incompatible)
        System.getLogger("name", null);
        // :: error: (argument.type.incompatible)
        log.log(null, "message");
        // :: error: (argument.type.incompatible)
        log.log(System.Logger.Level.INFO, (Object) null);
        // :: error: (argument.type.incompatible)
        log.log(System.Logger.Level.INFO, (Supplier<String>) null);
    }
}
