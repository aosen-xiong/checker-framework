// Nullness contracts documented by the core java.lang interfaces.

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.io.IOException;
import java.util.Iterator;
import java.util.Spliterator;

@AnnotatedFor("nullness")
class CoreInterfaceNullness {
    Appendable append(Appendable target, @Nullable CharSequence text) throws IOException {
        return target.append(text).append('x').append(text, 0, 0);
    }

    long text(CharSequence text) {
        return text.subSequence(0, 0).length()
                + text.toString().length()
                + text.chars().count()
                + text.codePoints().count();
    }

    void elements(Iterable<@Nullable String> source) {
        Iterator<@Nullable String> iterator = source.iterator();
        if (iterator.hasNext()) {
            String value = iterator.next();
            if (value != null) {
                value.length();
            }
        }
        source.forEach(value -> {
            if (value != null) {
                value.length();
            }
        });
        Spliterator<@Nullable String> spliterator = source.spliterator();
        spliterator.tryAdvance(value -> {
            if (value != null) {
                value.length();
            }
        });
    }

    void invalid(Iterable<@Nullable String> source, Readable reader, Comparable<String> ordering)
            throws IOException {
        // :: error: (argument.type.incompatible)
        source.forEach(null);
        // :: error: (argument.type.incompatible)
        reader.read(null);
        // :: error: (argument.type.incompatible)
        ordering.compareTo(null);
        // :: error: (dereference.of.nullable)
        source.iterator().next().length();
        source.forEach(value -> {
            // :: error: (dereference.of.nullable)
            value.length();
        });
    }

    void operations(AutoCloseable resource, Runnable action, Cloneable marker) throws Exception {
        action.run();
        resource.close();
        marker.getClass();
    }

    abstract static class RejectsNullAppend implements Appendable {
        @Override
        // :: error: (override.param.invalid)
        public abstract Appendable append(CharSequence text);
    }
}
