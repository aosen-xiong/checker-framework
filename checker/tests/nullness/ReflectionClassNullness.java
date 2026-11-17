// @below-java17-jdk-skip-test
// Class Javadoc: absent module lookup, record/annotated-superclass/sealed metadata.
import org.checkerframework.framework.qual.AnnotatedFor;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.RecordComponent;

@AnnotatedFor("nullness")
class ReflectionClassNullness {
    void require(Object value) {}

    void guarded(Class<?> type, Module module) {
        Class<?> found = Class.forName(module, "example.Missing");
        RecordComponent[] components = type.getRecordComponents();
        AnnotatedType superclass = type.getAnnotatedSuperclass();
        Class<?>[] permitted = type.getPermittedSubclasses();
        if (found != null) require(found.getName());
        if (components != null) {
            for (RecordComponent component : components) require(component.getName());
        }
        if (superclass != null) require(superclass.getType());
        if (permitted != null) {
            for (Class<?> subclass : permitted) require(subclass.getName());
        }
    }

    @SuppressWarnings("deprecation")
    <T> void successful(Class<T> type) throws ReflectiveOperationException {
        require(type.newInstance());
        require(Class.forName("java.lang.String", false, null));
        require(type.getMethod("toString", (Class<?>[]) null));
        require(type.getDeclaredMethods());
        require(type.getNestHost());
        for (Class<?> member : type.getNestMembers()) require(member.getName());
    }

    void invalid(Class<?> type, Module module) throws ReflectiveOperationException {
        // :: error: (dereference.of.nullable)
        Class.forName(module, "example.Missing").getName();
        // :: error: (dereference.of.nullable)
        type.getRecordComponents().clone();
        // :: error: (dereference.of.nullable)
        type.getAnnotatedSuperclass().getType();
        // :: error: (dereference.of.nullable)
        type.getPermittedSubclasses().clone();
        // :: error: (argument.type.incompatible)
        Class.forName(null, "example.Missing");
        // :: error: (argument.type.incompatible)
        Class.forName(module, null);
        // :: error: (argument.type.incompatible)
        type.getMethod(null);
        // :: error: (argument.type.incompatible)
        type.getMethod("example", new Class<?>[] {null});
    }
}
