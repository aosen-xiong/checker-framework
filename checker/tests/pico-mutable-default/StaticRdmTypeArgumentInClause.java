// A nested (implicitly static) class may use @ReceiverDependentMutable inside a type argument of
// its extends or implements clause. Like the clause itself (see StaticRdmExtendsClause), the type
// argument is part of the class bound: it follows the mutability of the class's own instance, not
// of a receiver, so it must not be reported as "static.receiverdependentmutable.forbidden".
//
// Reduced from java.util.Collections.CheckedMap.CheckedEntrySet, which implements
// Set<Map.@ReceiverDependentMutable Entry<K,V>>.

import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

@ReceiverDependentMutable class StaticRdmTypeArgumentInClause {
    @ReceiverDependentMutable static class Item {}

    @ReceiverDependentMutable interface Container<T extends @Readonly Object> {}

    @ReceiverDependentMutable static class Base<T extends @Readonly Object> {}

    @ReceiverDependentMutable static class Implements implements Container<@ReceiverDependentMutable Item> {}

    @ReceiverDependentMutable static class Extends extends Base<@ReceiverDependentMutable Item> {}

    @ReceiverDependentMutable static class Nested
            implements Container<
                    @ReceiverDependentMutable Container<@ReceiverDependentMutable Item>> {}

    static void staticMethod() {
        // A type use in a static method still has no receiver to depend on.
        // :: error: (static.receiverdependentmutable.forbidden)
        Container<@ReceiverDependentMutable Item> c = null;
    }
}
