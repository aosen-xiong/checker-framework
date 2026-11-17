// @below-java17-jdk-skip-test
// Module/ModuleLayer Javadoc: absent metadata, bootstrap loaders and startup boot layer.
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.io.IOException;
import java.lang.module.Configuration;
import java.util.List;
import java.util.function.Function;

@AnnotatedFor("nullness")
class ModuleLoadingNullness {
    void require(Object value) {}

    void module(Module module) throws IOException {
        if (module.isNamed()) {
            require(module.getName());
        }
        if (module.isNamed()) {
            require(module.getDescriptor());
        }
        String name = module.getName();
        if (name != null) require(name);
        ClassLoader loader = module.getClassLoader();
        if (loader != null) require(loader.getUnnamedModule());
        ModuleLayer layer = module.getLayer();
        if (layer != null) require(layer.configuration());
        require(module.getPackages());
        require(module.getAnnotations());
        for (var annotation : module.getDeclaredAnnotations()) require(annotation);
        var annotation = module.getAnnotation(Deprecated.class);
        if (annotation != null) require(annotation);
        var stream = module.getResourceAsStream("example/resource");
        if (stream != null) stream.close();
    }

    void layers(
            ModuleLayer layer,
            Configuration configuration,
            Function<String, ClassLoader> nonnull,
            Function<String, @Nullable ClassLoader> nullable) {
        require(layer.defineModulesWithOneLoader(configuration, null));
        require(layer.defineModulesWithManyLoaders(configuration, null));
        require(ModuleLayer.defineModulesWithOneLoader(configuration, List.of(layer), null));
        require(ModuleLayer.defineModulesWithManyLoaders(configuration, List.of(layer), null));
        require(layer.defineModules(configuration, nonnull));
        // A null callback result has documented LayerInstantiationException behavior.
        require(layer.defineModules(configuration, nullable));
        require(ModuleLayer.defineModules(configuration, List.of(layer), nullable));
        ModuleLayer boot = ModuleLayer.boot();
        if (boot != null) require(boot.configuration());
        ClassLoader loader = layer.findLoader("java.base");
        if (loader != null) require(loader.getUnnamedModule());
        for (Module module : layer.modules()) require(module);
        for (ModuleLayer parent : layer.parents()) require(parent);
        require(layer.findModule("java.base"));
        require(ModuleLayer.empty());
    }

    void invalid(Module module, ModuleLayer layer, Configuration configuration) throws IOException {
        // :: error: (dereference.of.nullable)
        module.getName().length();
        // :: error: (dereference.of.nullable)
        module.getDescriptor().name();
        // :: error: (dereference.of.nullable)
        module.getClassLoader().getUnnamedModule();
        // :: error: (dereference.of.nullable)
        module.getLayer().configuration();
        // :: error: (dereference.of.nullable)
        module.getResourceAsStream("example/resource").close();
        // :: error: (dereference.of.nullable)
        module.getAnnotation(Deprecated.class).since();
        // :: error: (dereference.of.nullable)
        layer.findLoader("java.base").getUnnamedModule();
        // :: error: (dereference.of.nullable)
        ModuleLayer.boot().configuration();
        // :: error: (argument.type.incompatible)
        module.isOpen("java.lang", null);
        // :: error: (argument.type.incompatible)
        module.getResourceAsStream(null);
        // :: error: (argument.type.incompatible)
        layer.defineModules(configuration, null);
        // :: error: (argument.type.incompatible)
        layer.findModule(null);
    }
}
