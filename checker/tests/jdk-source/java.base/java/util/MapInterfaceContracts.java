package java.util;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.function.Function;

/** Tests the JDK 17 map interfaces against the selected source tree. */
@AnnotatedFor("nullness")
class MapInterfaceContracts {
    void updates(Map<String, String> map, Map<String, @Nullable String> nullableValues) {
        map.getOrDefault("k", "fallback").length();
        map.computeIfAbsent("k", key -> "new").length();
        nullableValues.computeIfPresent("k", (key, old) -> old.trim());
        nullableValues.compute("k", (key, old) -> old == null ? "new" : old.trim());
        nullableValues.merge("k", "new", (left, right) -> left.trim() + right.trim());
        nullableValues.compute("k", (key, old) -> null);
        nullableValues.merge("k", "new", (left, right) -> null);
    }

    void ordering(SortedMap<String, String> sorted, NavigableMap<String, String> nav) {
        sorted.firstKey().length();
        sorted.lastKey().length();
        var comparator = sorted.comparator();
        if (comparator != null) {
            comparator.reversed();
        }
        var entry = nav.floorEntry("k");
        if (entry != null) {
            entry.getValue().length();
        }
        nav.descendingMap().navigableKeySet().iterator();
    }

    String copyEntry(Map.Entry<String, String> entry) {
        return Map.Entry.copyOf(entry).getValue();
    }

    @SuppressWarnings("nullness:argument.type.incompatible")
    Map<String, String> nullFactoryValue() {
        return Map.<String, String>of("k", null);
    }

    @SuppressWarnings("nullness:argument.type.incompatible")
    Map.Entry<String, String> nullEntryKey() {
        return Map.<String, String>entry(null, "v");
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int missing(Map<String, @Nullable String> map) {
        return map.get("absent").length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int storedNull(Map<String, @Nullable String> map) {
        return map.getOrDefault("k", "fallback").length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int declinedMapping(Map<String, String> map, Function<String, @Nullable String> mapping) {
        return map.computeIfAbsent("k", mapping).length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    void absentOldValue(Map<String, String> map) {
        map.compute("k", (key, old) -> old.trim());
    }

    @SuppressWarnings("nullness:argument.type.incompatible")
    void nullMergeOperand(Map<String, @Nullable String> map) {
        map.merge("k", null, (left, right) -> left);
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int noNeighbor(NavigableMap<String, String> nav) {
        return nav.floorKey("absent").length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    String emptyPoll(NavigableMap<String, String> nav) {
        return nav.pollFirstEntry().getValue();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int naturalOrder(SortedMap<String, String> sorted) {
        return sorted.comparator().hashCode();
    }
}
