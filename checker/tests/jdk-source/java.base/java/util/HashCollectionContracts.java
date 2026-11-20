package java.util;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

/** Caller contracts of hash-based collections and their concrete map views. */
@AnnotatedFor("nullness")
class HashCollectionContracts {
    void permitted(
            HashSet<@Nullable String> set,
            LinkedHashSet<@Nullable String> linked,
            HashMap<@Nullable String, @Nullable String> map) {
        set.add(null);
        linked.add(null);
        map.put(null, null);
        map.containsKey(null);
        map.compute(null, (k, old) -> old == null ? "first" : null);
        map.computeIfPresent(null, (k, present) -> present.isEmpty() ? null : present);
    }

    void presentArrays(
            HashSet<String> set,
            HashMap<String, String> map,
            LinkedHashMap<String, String> linked) {
        set.toArray()[0].hashCode();
        map.keySet().toArray()[0].hashCode();
        linked.values().toArray()[0].hashCode();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int absent(HashMap<String, String> map) {
        return map.computeIfPresent("missing", (k, v) -> "x").length();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int nullableSet(HashSet<@Nullable String> set) {
        return set.toArray()[0].hashCode();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int nullableOrderedSet(LinkedHashSet<@Nullable String> set) {
        return set.toArray()[0].hashCode();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int nullableKey(HashMap<@Nullable String, String>.KeySet keys) {
        return keys.toArray()[0].hashCode();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int nullableValue(HashMap<String, @Nullable String>.Values values) {
        return values.toArray()[0].hashCode();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int nullableOrderedKey(LinkedHashMap<@Nullable String, String>.LinkedKeySet keys) {
        return keys.toArray()[0].hashCode();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int nullableOrderedValue(LinkedHashMap<String, @Nullable String>.LinkedValues values) {
        return values.toArray()[0].hashCode();
    }

    @SuppressWarnings("nullness:dereference.of.nullable")
    int missingBin(HashMap<String, String> map) {
        return map.getNode("missing").hashCode();
    }
}
