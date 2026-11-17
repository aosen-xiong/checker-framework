import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

@AnnotatedFor("nullness")
class TraversalNullness {
    void genericElements(
            Iterator<String> nonnull,
            Iterator<@Nullable String> nullable,
            ListIterator<String> list,
            ListIterator<@Nullable String> nullableList,
            Enumeration<String> enumeration,
            Enumeration<@Nullable String> nullableEnumeration) {
        nonnull.next().length();
        // :: error: (dereference.of.nullable)
        nullable.next().length();
        list.previous().length();
        // :: error: (dereference.of.nullable)
        nullableList.previous().length();
        nullableList.add(null);
        nullableList.set(null);
        // :: error: (argument.type.incompatible)
        list.add(null);
        // :: error: (argument.type.incompatible)
        list.set(null);
        enumeration.nextElement().length();
        enumeration.asIterator().next().length();
        // :: error: (dereference.of.nullable)
        nullableEnumeration.nextElement().length();
        // :: error: (dereference.of.nullable)
        nullableEnumeration.asIterator().next().length();
        nullable.forEachRemaining(value -> {
            if (value != null) value.length();
        });
        // :: error: (argument.type.incompatible)
        nonnull.forEachRemaining(null);
    }

    void splitting(
            Spliterator<String> refs,
            Spliterator.OfInt ints,
            Spliterator.OfLong longs,
            Spliterator.OfDouble doubles) {
        // :: error: (dereference.of.nullable)
        refs.trySplit().estimateSize();
        // :: error: (dereference.of.nullable)
        ints.trySplit().estimateSize();
        // :: error: (dereference.of.nullable)
        longs.trySplit().estimateSize();
        // :: error: (dereference.of.nullable)
        doubles.trySplit().estimateSize();
        // :: error: (dereference.of.nullable)
        refs.getComparator().compare("a", "b");
        Spliterator<String> split = refs.trySplit();
        if (split != null) split.estimateSize();
        // :: error: (argument.type.incompatible)
        refs.tryAdvance(null);
        // :: error: (argument.type.incompatible)
        refs.forEachRemaining(null);
        // :: error: (argument.type.incompatible)
        ints.tryAdvance((IntConsumer) null);
        // :: error: (argument.type.incompatible)
        longs.forEachRemaining((LongConsumer) null);
        // :: error: (argument.type.incompatible)
        doubles.tryAdvance((Consumer<Double>) null);
    }

    void factories(
            String[] nonnull,
            @Nullable String[] nullable,
            Iterator<@Nullable String> iterator,
            Collection<@Nullable String> collection,
            Spliterator<@Nullable String> split) {
        Spliterator<String> good = Spliterators.spliterator(nonnull, 0);
        Spliterator<String> goodRange = Spliterators.spliterator(nonnull, 0, nonnull.length, 0);
        Spliterator<@Nullable String> maybe = Spliterators.spliterator(nullable, 0);
        Spliterator<@Nullable String> maybeRange =
                Spliterators.spliterator(nullable, 0, nullable.length, 0);
        Spliterator<@Nullable String> copied = Spliterators.spliterator(collection, 0);
        Spliterator<@Nullable String> counted = Spliterators.spliterator(iterator, 1, 0);
        Spliterator<@Nullable String> unknown = Spliterators.spliteratorUnknownSize(iterator, 0);
        Spliterator<@Nullable String> empty = Spliterators.emptySpliterator();
        // :: error: (dereference.of.nullable)
        Spliterators.iterator(split).next().length();
        // :: error: (assignment.type.incompatible)
        Spliterator<String> bad = Spliterators.spliterator(nullable, 0);
        // :: error: (assignment.type.incompatible)
        Spliterator<String> badRange = Spliterators.spliterator(nullable, 0, nullable.length, 0);
        // :: error: (argument.type.incompatible)
        Spliterators.spliterator((Object[]) null, 0);
        // :: error: (argument.type.incompatible)
        Spliterators.spliterator((int[]) null, 0);
        // :: error: (argument.type.incompatible)
        Spliterators.spliterator((Collection<String>) null, 0);
        // :: error: (argument.type.incompatible)
        Spliterators.spliteratorUnknownSize((Iterator<String>) null, 0);
        // :: error: (argument.type.incompatible)
        Spliterators.iterator((Spliterator<String>) null);
    }

    void primitiveAdapters() {
        PrimitiveIterator.OfInt ints =
                Spliterators.iterator(Spliterators.spliterator(new int[] {1}, 0));
        PrimitiveIterator.OfLong longs =
                Spliterators.iterator(Spliterators.spliterator(new long[] {1}, 0));
        PrimitiveIterator.OfDouble doubles =
                Spliterators.iterator(Spliterators.spliterator(new double[] {1}, 0));
        ints.next().intValue();
        longs.next().longValue();
        doubles.next().doubleValue();
        // :: error: (argument.type.incompatible)
        ints.forEachRemaining((IntConsumer) null);
        // :: error: (argument.type.incompatible)
        longs.forEachRemaining((LongConsumer) null);
        // :: error: (argument.type.incompatible)
        doubles.forEachRemaining((DoubleConsumer) null);
    }

    void genericCallbacks(
            PrimitiveIterator<Integer, @Nullable IntConsumer> iterator,
            Spliterator.OfPrimitive<Integer, @Nullable IntConsumer, ?> split) {
        iterator.forEachRemaining((int value) -> {});
        // :: error: (argument.type.incompatible)
        iterator.forEachRemaining((IntConsumer) null);
        // :: error: (argument.type.incompatible)
        split.tryAdvance((IntConsumer) null);
        // :: error: (argument.type.incompatible)
        split.forEachRemaining((IntConsumer) null);
    }
}
