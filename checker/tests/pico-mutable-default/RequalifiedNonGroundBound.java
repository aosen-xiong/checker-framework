import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// A use @q X is viewed as the bound of X with main qualifier q only when the type arguments of the
// bound are ground (GAIT rule ST-Qual-Class). When they contain a type variable, a later
// substitution can supply a receiver-dependent qualifier that q has just replaced, so no field or
// method is reachable through @q X, and @q X is not a subtype of a generic class type.
@ReceiverDependentMutable class NonGroundNode<T extends @Readonly NonGroundNode<T>> {
    T next;
    int value;

    NonGroundNode(T next) {
        this.next = next;
    }

    T getNext(@Readonly NonGroundNode<T> this) {
        return next;
    }

    void bump(@Mutable NonGroundNode<T> this) {
        value++;
    }
}

// Through an @Immutable NonGroundSelf, next is an @Immutable NonGroundSelf.
@ReceiverDependentMutable class NonGroundSelf extends NonGroundNode<@ReceiverDependentMutable NonGroundSelf> {
    NonGroundSelf(@ReceiverDependentMutable NonGroundSelf next) {
        super(next);
    }
}

class RequalifiedNonGroundBound {
    // With T = @Mutable NonGroundSelf, n is an @Immutable NonGroundSelf, whose next is immutable.
    static <T extends @Mutable NonGroundNode<T>> void walk(@Immutable T n) {
        // :: error: (type.variable.use.member) :: error: (method.invocation.invalid)
        n.getNext().bump();
        // :: error: (type.variable.use.member)
        n.next.bump();
        // :: error: (type.variable.use.member)
        int v = n.value;
        // :: error: (assignment.type.incompatible)
        @Readonly NonGroundNode<T> view = n;
        // Object has no type arguments, so it exposes no member typed by T.
        @Readonly Object o = n;
    }

    static <T extends @Mutable NonGroundNode<T>> void readonlyUse(@Readonly T n) {
        // :: error: (type.variable.use.member)
        n.next.bump();
    }

    // A bare use stands for the whole type argument, so its members are reachable.
    static <T extends @Mutable NonGroundNode<T>> void plain(T n) {
        n.getNext().bump();
        n.next.bump();
    }

    static void caller(@Immutable NonGroundSelf s) {
        RequalifiedNonGroundBound.<@Mutable NonGroundSelf>walk(s);
    }
}
