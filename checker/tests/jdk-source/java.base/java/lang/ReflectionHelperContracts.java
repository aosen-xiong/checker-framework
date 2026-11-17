// Check through typecheck-jdk.sh with this module root in JDK_EXTRA_SOURCE_ROOTS.
package java.lang;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.lang.reflect.Method;
import java.util.function.BiFunction;

@AnnotatedFor("nullness")
class ReflectionHelperContracts {
    int methods(Method[] methods, String name, Class<?>[] parameterTypes) {
        PublicMethods collection = new PublicMethods();
        for (Method method : methods) collection.merge(method);
        PublicMethods.MethodList list =
                PublicMethods.MethodList.filter(methods, name, parameterTypes, true);
        list = PublicMethods.MethodList.merge(list, null);
        list = PublicMethods.MethodList.merge(null, list);
        return collection.toArray().length
                + (list == null ? 0 : list.getMostSpecific().getName().length());
    }

    int weak(
            WeakPairMap<Object, Object, String> map,
            Object first,
            Object second,
            BiFunction<Object, Object, String> nonnull,
            BiFunction<Object, Object, @Nullable String> nullable) {
        String previous = map.put(first, second, "value");
        String absent = map.putIfAbsent(first, second, "value");
        String found = map.get(first, second);
        String computed = map.computeIfAbsent(first, second, nullable);
        int result = map.computeIfAbsent(first, second, nonnull).length();
        for (String value : map.values()) result += value.length();
        return result
                + (previous == null ? 0 : previous.length())
                + (absent == null ? 0 : absent.length())
                + (found == null ? 0 : found.length())
                + (computed == null ? 0 : computed.length());
    }

    <T> T liveEntry(ClassValue<T> value, ClassValue.@Nullable Entry<T> entry) {
        if (value.match(entry)) return value.castEntry(entry).value();
        return value.get(Object.class);
    }

    void classInternals(Class<?> type) {
        Object data = type.getClassData();
        if (data != null) data.toString();
        byte[] annotations = type.getRawTypeAnnotations();
        if (annotations != null) annotations.clone();
        if (type.getConstantPool() != null) type.getConstantPool().getSize();
    }
}
