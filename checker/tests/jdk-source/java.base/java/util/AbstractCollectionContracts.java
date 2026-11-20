package java.util;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

/** Caller checks for the abstract collections and their package-visible contracts. */
@AnnotatedFor("nullness")
class AbstractCollectionContracts {
    void arrays(AbstractCollection<String> values, AbstractCollection<@Nullable String> nullable) {
        values.toArray()[0].hashCode();
        values.toArray(new String[0])[0].length();
        @Nullable Object[] result = nullable.toArray();
        if (result.length > 0 && result[0] != null) {
            result[0].hashCode();
        }
    }

    void mapViews(AbstractMap<String, String> map) {
        map.keySet().size();
        map.values().size();
        var cached = map.keySet;
        if (cached != null) {
            cached.size();
        }
    }

    void traversal(
            AbstractList<String> list,
            AbstractSequentialList<String> sequential,
            AbstractSet<@Nullable String> set,
            AbstractQueue<String> queue) {
        list.subList(0, list.size()).listIterator();
        sequential.listIterator().next().length();
        set.equals(null);
        queue.element().length();
        queue.remove().length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int nullableArray(AbstractCollection<@Nullable String> values) {
        return values.toArray()[0].hashCode();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int nullableList(AbstractSequentialList<@Nullable String> list) {
        return list.get(0).length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    long exhaustedSplit(AbstractList.RandomAccessSpliterator<String> spliterator) {
        return spliterator.trySplit().estimateSize();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int missingKeysView(AbstractMap<String, String> map) {
        return map.keySet.size();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int missingValuesView(AbstractMap<String, String> map) {
        return map.values.size();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int nullableEntry(AbstractMap.SimpleEntry<String, @Nullable String> entry) {
        return entry.getValue().length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int emptyQueue(AbstractQueue<String> queue) {
        return queue.poll().length();
    }
}
