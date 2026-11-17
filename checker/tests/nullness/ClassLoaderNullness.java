// @below-java17-jdk-skip-test
// ClassLoader Javadoc: nullable module lookups, package absence and optional metadata.
import org.checkerframework.framework.qual.AnnotatedFor;

import java.io.IOException;
import java.net.URL;

@AnnotatedFor("nullness")
class ClassLoaderNullness extends ClassLoader {
    ClassLoaderNullness() {
        super(null, null);
    }

    void require(Object value) {}

    void valid() throws IOException, ClassNotFoundException {
        require(loadClass("java.lang.String"));
        require(findSystemClass("java.lang.String"));
        Class<?> found = findClass(null, "example.Missing");
        if (found != null) require(found.getName());
        URL resource = findResource(null, "example/resource");
        if (resource != null) require(resource.getPath());
        Package defined = getDefinedPackage("example");
        if (defined != null) require(defined.getName());
        require(definePackage("example", null, null, null, null, null, null, null));
        require(getUnnamedModule());
        for (Package pkg : getDefinedPackages()) require(pkg.getName());
        for (Package pkg : getPackages()) require(pkg.getName());
        require(getResources("example/resource"));
        require(findResources("example/resource"));
        require(ClassLoader.getPlatformClassLoader());
        require(ClassLoader.getSystemClassLoader());
        setPackageAssertionStatus(null, true);
    }

    void invalid() throws IOException {
        // :: error: (dereference.of.nullable)
        findClass(null, "example.Missing").getName();
        // :: error: (dereference.of.nullable)
        findResource(null, "example/resource").getPath();
        // :: error: (dereference.of.nullable)
        getDefinedPackage("example").getName();
        // :: error: (argument.type.incompatible)
        getDefinedPackage(null);
        // :: error: (argument.type.incompatible)
        getResource(null);
    }
}
