package java.util;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

/** Null-element, empty-queue and internal split contracts of the four concrete collections. */
@AnnotatedFor("nullness")
class ListQueueContracts {
    void lists(ArrayList<@Nullable String> array, LinkedList<@Nullable String> linked) {
        array.add(null);
        linked.addFirst(null);
        array.sort(null);
        Object[] safe = new ArrayList<String>().toArray();
        var elements = linked.toArray();
        if (elements.length > 0 && elements[0] != null) {
            elements[0].hashCode();
        }
    }

    void queues(
            ArrayDeque<String> deque,
            PriorityQueue<String> heap,
            @Nullable Comparator<String> comparator) {
        deque.add("a");
        heap.offer("b");
        deque.toArray()[0].hashCode();
        heap.toArray()[0].hashCode();
        deque.getFirst().length();
        var first = heap.peek();
        if (first != null) {
            first.length();
        }
        var order = heap.comparator();
        if (order != null) {
            order.compare("a", "b");
        }
        new PriorityQueue<String>(comparator);
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int arrayElement(ArrayList<@Nullable String> values) {
        return values.toArray()[0].hashCode();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int linkedElement(LinkedList<@Nullable String> values) {
        return values.toArray()[0].hashCode();
    }

    @SuppressWarnings("nullness:argument.type.incompatible")
    void nullDequeElement(ArrayDeque<String> deque) {
        deque.addFirst(null);
    }

    @SuppressWarnings("nullness:argument.type.incompatible")
    void nullHeapElement(PriorityQueue<String> heap) {
        heap.offer(null);
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int emptyDeque(ArrayDeque<String> deque) {
        return deque.pollFirst().length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int absentComparator(PriorityQueue<String> heap) {
        return heap.comparator().compare("a", "b");
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    long exhaustedSplit(ArrayList<String>.ArrayListSpliterator split) {
        return split.trySplit().estimateSize();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int noMovedEntry(PriorityQueue<String> heap) {
        return heap.removeAt(0).length();
    }
}
