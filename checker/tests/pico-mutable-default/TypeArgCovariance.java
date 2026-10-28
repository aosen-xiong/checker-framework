import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.MutabilityLost;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// Limited covariance for type arguments: the head qualifier of a type argument may widen from
// @Mutable, @Immutable, or @Readonly to @MutabilityLost. @ReceiverDependentMutable does not widen,
// and no other widening is allowed. The outer head qualifier uses ordinary subtyping.
//
// The test writes @MutabilityLost in type arguments and local variable types on purpose, so it
// suppresses the diagnostics that reject those declarations. assignment.type.incompatible is then
// the only diagnostic about the subtype relation itself.
@SuppressWarnings({"mutability.lost.type.argument", "mutability.lost.lhs"})
@ReceiverDependentMutable class TypeArgCovariance {
    @ReceiverDependentMutable class Box<T extends @Readonly Object> {}

    void mutableWidens(@Mutable Box<@Mutable Object> b) {
        @Mutable Box<@MutabilityLost Object> x = b;
    }

    void immutableWidens(@Mutable Box<@Immutable Object> b) {
        @Mutable Box<@MutabilityLost Object> x = b;
    }

    void readonlyWidens(@Mutable Box<@Readonly Object> b) {
        @Mutable Box<@MutabilityLost Object> x = b;
    }

    void receiverDependentDoesNotWiden(@Mutable Box<@ReceiverDependentMutable Object> b) {
        // :: error: (assignment.type.incompatible)
        @Mutable Box<@MutabilityLost Object> x = b;
    }

    void lostDoesNotNarrow(@Mutable Box<@MutabilityLost Object> b) {
        // :: error: (assignment.type.incompatible)
        @Mutable Box<@Mutable Object> x = b;
    }

    void noGeneralCovariance(@Mutable Box<@Mutable Object> b) {
        // :: error: (assignment.type.incompatible)
        @Mutable Box<@Readonly Object> x = b;
    }

    void noWideningBetweenConcreteQualifiers(@Mutable Box<@Immutable Object> b) {
        // :: error: (assignment.type.incompatible)
        @Mutable Box<@Mutable Object> x = b;
    }

    void outerHeadUsesOrdinarySubtyping(@Mutable Box<@Mutable Object> b) {
        @Readonly Box<@MutabilityLost Object> x = b;
    }

    void outerHeadStillChecked(@Readonly Box<@Mutable Object> b) {
        // :: error: (assignment.type.incompatible)
        @Mutable Box<@MutabilityLost Object> x = b;
    }

    void nestedArgumentWidens(@Mutable Box<@Mutable Box<@Mutable Object>> b) {
        @Mutable Box<@Mutable Box<@MutabilityLost Object>> x = b;
    }

    void nestedHeadWidens(@Mutable Box<@Mutable Box<@Mutable Object>> b) {
        @Mutable Box<@MutabilityLost Box<@MutabilityLost Object>> x = b;
    }

    void nestedNoGeneralCovariance(@Mutable Box<@Mutable Box<@Mutable Object>> b) {
        // :: error: (assignment.type.incompatible)
        @Mutable Box<@Mutable Box<@Readonly Object>> x = b;
    }

    <T extends @Readonly Object> void qualifiedTypeVariableUseWidens(@Mutable Box<@Mutable T> b) {
        @Mutable Box<@MutabilityLost T> x = b;
    }

    <T extends @Readonly Object> void bareTypeVariableUseDoesNotWiden(@Mutable Box<T> b) {
        // :: error: (assignment.type.incompatible)
        @Mutable Box<@MutabilityLost T> x = b;
    }

    // The type hierarchy memoizes containment results. A failing comparison of the same types
    // comes first in source order, so a cached result must not hide the later widening.
    @Mutable Box<@Mutable Object> shared = new @Mutable Box<@Mutable Object>();

    void sharedNarrowFirst() {
        // :: error: (assignment.type.incompatible)
        @Mutable Box<@Immutable Object> x = shared;
    }

    void sharedWidenAfterNarrow() {
        @Mutable Box<@MutabilityLost Object> x = shared;
    }

    void sharedWidenAgain() {
        @Mutable Box<@MutabilityLost Object> x = shared;
    }

    @Mutable Box<@Mutable Box<@Mutable Object>> nestedShared =
            new @Mutable Box<@Mutable Box<@Mutable Object>>();

    void nestedSharedNarrowFirst() {
        // :: error: (assignment.type.incompatible)
        @Mutable Box<@Mutable Box<@Immutable Object>> x = nestedShared;
    }

    void nestedSharedWidenAfterNarrow() {
        @Mutable Box<@Mutable Box<@MutabilityLost Object>> x = nestedShared;
    }

    void nestedSharedWidenAgain() {
        @Mutable Box<@Mutable Box<@MutabilityLost Object>> x = nestedShared;
    }
}
