// Test case: a nested (implicitly static) interface may extend a
// @ReceiverDependentMutable type. The extends/implements clause names a CLASS
// BOUND, not a type use that needs a receiver, so @ReceiverDependentMutable is
// legal there and must NOT be reported as
// "static.receiverdependentmutable.forbidden".
//
// Reduced from java.util.Spliterator in the annotated JDK, where
// Spliterator.OfPrimitive and Spliterator.OfInt hit exactly this shape and
// produced 90 false positives across the JCF benchmark.

import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

@ReceiverDependentMutable interface StaticRdmExtendsClause<T extends @Readonly Object> {

    // nested + @ReceiverDependentMutable + extends an RDM type
    @ReceiverDependentMutable interface OfPrim<T extends @Readonly Object, S extends StaticRdmExtendsClause.OfPrim<T, S>>
            extends StaticRdmExtendsClause<T> {
        S trySplit(@Mutable OfPrim<T, S> this);
    }

    // and a further nesting level, extending the nested RDM interface
    @ReceiverDependentMutable interface OfInt extends OfPrim<Integer, OfInt> {
        @Override
        OfInt trySplit(@Mutable OfInt this);
    }
}
