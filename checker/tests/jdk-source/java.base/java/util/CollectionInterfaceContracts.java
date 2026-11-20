package java.util;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

/** Caller checks against the enrolled JDK sources. CI reports unused suppressions as errors. */
@AnnotatedFor("nullness")
class CollectionInterfaceContracts {
    void nonnullElements(Collection<String> names, List<String> list, Set<String> set) {
        String[] result = names.toArray(String[]::new);
        CharSequence[] wider = names.toArray(CharSequence[]::new);
        result[0].length();
        wider[0].length();
        list.toArray(String[]::new)[0].length();
        set.toArray(String[]::new)[0].length();
    }

    int guardedElement(Collection<@Nullable String> names) {
        @Nullable String[] result = names.toArray(String[]::new);
        if (result.length == 0) {
            return 0;
        }
        var name = result[0];
        return name == null ? 0 : name.length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int nullableElement(Collection<@Nullable String> names) {
        return names.toArray(String[]::new)[0].length();
    }

    @SuppressWarnings("nullness:assignment.type.incompatible")
    void nullableAssignment(Collection<@Nullable String> names) {
        String[] result = names.toArray(String[]::new);
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int inheritedListElement(List<@Nullable String> names) {
        return names.toArray(String[]::new)[0].length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int inheritedSetElement(Set<@Nullable String> names) {
        return names.toArray(String[]::new)[0].length();
    }

    boolean usesNaturalOrder(List<String> list, SortedSet<String> sorted) {
        list.sort(null);
        return sorted.spliterator().getComparator() == null;
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    void missingComparator(SortedSet<String> sorted) {
        sorted.spliterator().getComparator().reversed();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int absentNeighbor(NavigableSet<String> names) {
        return names.floor("a").length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int emptyQueue(Queue<String> names) {
        return names.peek().length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int emptyDeque(Deque<String> names) {
        return names.pollLast().length();
    }

    void wrapperElements(
            Collections.UnmodifiableCollection<String> unmodifiable,
            Collections.SynchronizedCollection<String> synchronizedView,
            Collections.CheckedCollection<String> checked,
            Collections.AsLIFOQueue<String> lifo) {
        unmodifiable.toArray(String[]::new)[0].length();
        synchronizedView.toArray(String[]::new)[0].length();
        checked.toArray(String[]::new)[0].length();
        lifo.toArray(String[]::new)[0].length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    void nullableWrapperElements(
            Collections.UnmodifiableCollection<@Nullable String> unmodifiable,
            Collections.SynchronizedCollection<@Nullable String> synchronizedView,
            Collections.CheckedCollection<@Nullable String> checked,
            Collections.AsLIFOQueue<@Nullable String> lifo) {
        unmodifiable.toArray(String[]::new)[0].length();
        synchronizedView.toArray(String[]::new)[0].length();
        checked.toArray(String[]::new)[0].length();
        lifo.toArray(String[]::new)[0].length();
    }

    void throwingAccessors(Queue<String> queue, Deque<String> deque) {
        queue.element().length();
        deque.getFirst().length();
    }
}
