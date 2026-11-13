import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.MutabilityLost;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// A written @MutabilityLost type argument, as the paper's rules allow it (STA-Var, STA-Class):
// type-argument subtyping lets a qualifier be replaced by @MutabilityLost, so
// @Readonly LostViewPair<@MutabilityLost L, @MutabilityLost R> is a supertype of every
// LostViewPair, even when the type parameters have @ReceiverDependentMutable bounds. Such a type
// may be the declared type of a local variable, a parameter, a field or a return. Lost stays
// forbidden where the paper forbids it: in the type arguments of an object creation (ST-New), of a
// superclass (WF-Class) and of a method call (ST-Call), and wherever a value is written at a type
// that contains Lost: a field (ST-FldWrite), a parameter (ST-Call) or an array element.

// A pair whose element mutability follows the pair's, as in the Commons Lang tuple study.
@ReceiverDependentMutable class LostViewPair<
        L extends @ReceiverDependentMutable Object, R extends @ReceiverDependentMutable Object> {
    // No fields: a type-variable field with the receiver-dependent default makes its getter fail
    // (a separate checker defect), and the rules tested here concern only the pair's type.
    LostViewPair(L left, R right) {}

    LostViewPair() {}

    L getLeft() {
        return null;
    }
}

class GaitLostTypeArgumentViews {
    static class Data {
        int x;
    }

    @Immutable static final class Point {
        final int x;

        Point(int x) {
            this.x = x;
        }
    }

    // Allowed: a read-only view of any pair, in a local variable.

    void viewOfImmutable(@Immutable LostViewPair<@Immutable Point, @Immutable String> ip) {
        @Readonly LostViewPair<@MutabilityLost Point, @MutabilityLost String> any = ip;
        @Readonly Point a = any.getLeft();
    }

    void viewOfMutable(@Mutable LostViewPair<@Mutable Data, @Mutable Data> mp) {
        @Readonly LostViewPair<@MutabilityLost Data, @MutabilityLost Data> any = mp;
        @Readonly Data b = any.getLeft();
    }

    void reassignLocal(
            @Immutable LostViewPair<@Immutable Object, @Immutable Object> ip,
            @Mutable LostViewPair<@Mutable Object, @Mutable Object> mp) {
        @Readonly LostViewPair<@MutabilityLost Object, @MutabilityLost Object> any = ip;
        any = mp;
    }

    // Allowed: the same view as a return type and as a parameter declaration.

    @Readonly LostViewPair<@MutabilityLost Data, @MutabilityLost Data> asView(
            @Mutable LostViewPair<@Mutable Data, @Mutable Data> mp) {
        return mp;
    }

    static void takeView(@Readonly LostViewPair<@MutabilityLost Data, @MutabilityLost Data> p) {
        @Readonly Data l = p.getLeft();
        // A parameter is a local variable: it may be reassigned.
        p = p;
    }

    static <L extends @Readonly Object> void takeGeneric(
            @Readonly LostViewPair<@MutabilityLost L, @MutabilityLost L> p) {
        @Readonly Object l = p.getLeft();
    }

    static void takeWild(
            @Readonly LostViewPair<? extends @MutabilityLost Object, ? extends @MutabilityLost Object>
                            p) {
        @Readonly Object l = p.getLeft();
    }

    // Allowed: a field declared with Lost, read but never written.
    @Readonly LostViewPair<@MutabilityLost Data, @MutabilityLost Data> fieldView;

    void readField() {
        @Readonly LostViewPair<@MutabilityLost Data, @MutabilityLost Data> v = fieldView;
    }

    // A Lost element is only readonly: it cannot be read as mutable.
    void lostIsNotMutable(@Mutable LostViewPair<@Mutable Data, @Mutable Data> mp) {
        @Readonly LostViewPair<@MutabilityLost Data, @MutabilityLost Data> any = mp;
        // :: error: (assignment.type.incompatible)
        @Mutable Data m = any.getLeft();
    }

    // Forbidden: passing a value at a parameter type that contains Lost (ST-Call).
    void callers(@Mutable LostViewPair<@Mutable Data, @Mutable Data> mp) {
        // :: error: (mutability.lost.parameter)
        takeView(mp);
        // :: error: (mutability.lost.parameter)
        takeGeneric(mp);
        // :: error: (argument.type.incompatible) :: error: (mutability.lost.parameter)
        takeWild(mp);
    }

    // Forbidden: writing a field whose type contains Lost (ST-FldWrite), by assignment or
    // initializer.
    void writeField(@Mutable LostViewPair<@Mutable Data, @Mutable Data> mp) {
        // :: error: (mutability.lost.lhs)
        fieldView = mp;
    }

    // :: error: (mutability.lost.lhs)
    @Readonly LostViewPair<@MutabilityLost Data, @MutabilityLost Data> initializedField = null;

    // Forbidden: writing an array element whose type contains Lost.
    void writeArray(
            @Readonly LostViewPair<@MutabilityLost Data, @MutabilityLost Data> @Mutable [] views,
            @Mutable LostViewPair<@Mutable Data, @Mutable Data> mp) {
        // :: error: (mutability.lost.lhs)
        views[0] = mp;
    }

    // Forbidden: Lost in the type arguments of an object creation (ST-New).
    void create(@Mutable Data d) {
        // :: error: (mutability.lost.type.argument) :: error: (type.argument.type.incompatible)
        // :: error: (argument.type.incompatible) :: error: (mutability.lost.parameter)
        new @Mutable LostViewPair<@MutabilityLost Data, @Mutable Data>(d, d);
    }

    // Forbidden: Lost in the type arguments of a superclass (WF-Class).
    @ReceiverDependentMutable static class Sub
            // :: error: (mutability.lost.type.argument) :: error: (type.argument.type.incompatible)
            // :: error: (mutability.lost.type.argument) :: error: (type.argument.type.incompatible)
            extends LostViewPair<@MutabilityLost Data, @MutabilityLost Data> {}

    // Forbidden: Lost in the type arguments of a method call (ST-Call).
    static <T extends @Readonly Object> T id(T t) {
        return t;
    }

    void callWithLost(@Mutable Data d) {
        // :: error: (mutability.lost.type.argument) :: error: (argument.type.incompatible)
        // :: error: (mutability.lost.parameter)
        GaitLostTypeArgumentViews.<@MutabilityLost Data>id(d);
    }
}
