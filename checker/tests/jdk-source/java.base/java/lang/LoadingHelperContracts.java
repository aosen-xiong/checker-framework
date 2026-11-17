// Check through typecheck-jdk.sh with this module root in JDK_EXTRA_SOURCE_ROOTS.
package java.lang;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.util.Enumeration;

@AnnotatedFor("nullness")
class LoadingHelperContracts {
    void require(Object value) {}

    void metadata(ModuleDescriptor descriptor, Module named, ClassLoader loader, Class<?> type) {
        Module unnamed = new Module((ClassLoader) null);
        require(unnamed);
        require(new Module(null, null, descriptor, null));
        require(new Module(null, descriptor));
        Package pkg = new Package("example", null, null, null, null, null, null, null, null);
        require(pkg.getName());
        require(Package.VersionInfo.getInstance(null, null, null, null, null, null, null));
        NamedPackage reference = new NamedPackage("example", named);
        URI location = reference.location();
        if (location != null) require(location);
        require(reference.module());
        require(reference.packageName());
        Package defined = loader.definePackage(type);
        if (defined != null) require(defined.getName());
        require(loader.createOrGetClassLoaderValueMap());
        ClassLoader.checkClassLoaderPermission(loader, null);
        ClassLoader callerLoader = ClassLoader.getClassLoader(null);
        if (callerLoader != null) require(callerLoader);
    }

    void namedDescriptor(Module module) {
        if (module.isNamed()) require(module.getDescriptor());
    }

    void namedName(Module module) {
        if (module.isNamed()) require(module.getName());
    }

    <T extends @Nullable Object> T enumeration(Enumeration<T>[] elements) {
        return new CompoundEnumeration<>(elements).nextElement();
    }
}
