// @below-java17-jdk-skip-test
// @above-java17-jdk-skip-test
// Pinned JDK 17 contracts for APIs removed or disabled in later releases.
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.security.Permission;

@AnnotatedFor("nullness")
@SuppressWarnings("removal")
class LegacySecurityNullness {
    void require(Object value) {}

    void valid(SecurityManager manager, Permission permission) {
        manager.checkPermission(permission, null);
        manager.checkRead("file", null);
        manager.checkConnect("localhost", 80, null);
        require(manager.getSecurityContext());
        ThreadGroup group = manager.getThreadGroup();
        if (group != null) require(group);
        System.setSecurityManager(null);
        SecurityManager current = System.getSecurityManager();
        if (current != null) require(current);
        require(new RuntimePermission("name", null));
        Object answer = Compiler.command("command");
        if (answer != null) require(answer);
        Compiler.compileClass(String.class);
        Compiler.compileClasses("java.lang.*");
    }

    static class NoPreferredGroup extends SecurityManager {
        @Override
        public @Nullable ThreadGroup getThreadGroup() {
            return null;
        }
    }

    void invalid(SecurityManager manager) {
        // :: error: (dereference.of.nullable)
        manager.getThreadGroup().getName();
        // :: error: (dereference.of.nullable)
        System.getSecurityManager().getSecurityContext();
        // :: error: (dereference.of.nullable)
        Compiler.command("command").toString();
        // :: error: (argument.type.incompatible)
        manager.checkPermission(null);
        // :: error: (argument.type.incompatible)
        manager.checkRead((String) null);
        // :: error: (argument.type.incompatible)
        manager.checkConnect(null, 80);
        // :: error: (argument.type.incompatible)
        new RuntimePermission(null);
        // :: error: (argument.type.incompatible)
        Compiler.command(null);
        // :: error: (argument.type.incompatible)
        Compiler.compileClass(null);
        // :: error: (argument.type.incompatible)
        Compiler.compileClasses(null);
    }
}
