import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class ClassValueNullness {
    static final ClassValue<@Nullable String> NULLABLE =
            new ClassValue<@Nullable String>() {
                @Override
                protected @Nullable String computeValue(Class<?> type) {
                    return null;
                }
            };
    static final ClassValue<String> NONNULL =
            new ClassValue<String>() {
                @Override
                protected String computeValue(Class<?> type) {
                    return type.getName();
                }
            };

    <T> T preserve(ClassValue<T> value, Class<?> type) {
        return value.get(type);
    }

    int valid(Class<?> type) {
        String value = NULLABLE.get(type);
        NULLABLE.remove(type);
        return NONNULL.get(type).length() + (value == null ? 0 : value.length());
    }

    void invalid() {
        // :: error: (dereference.of.nullable)
        NULLABLE.get(String.class).length();
        // :: error: (argument.type.incompatible)
        NONNULL.get(null);
        // :: error: (argument.type.incompatible)
        NONNULL.remove(null);
    }
}
