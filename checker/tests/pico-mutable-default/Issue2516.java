import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// https://github.com/eisop/checker-framework/issues/2516
// A class type parameter's bound is viewpoint-adapted through the receiver of a member access,
// like a field declared with the same qualifier, and a method type parameter bound that names the
// class type parameter is adapted with it.
@ReceiverDependentMutable class Issue2516Box<X extends @ReceiverDependentMutable Object> {
    @ReceiverDependentMutable Object f;
    X item;

    Issue2516Box(@ReceiverDependentMutable Object f, X item) {
        this.f = f;
        this.item = item;
    }

    // The receiver is the class's own type, so its type argument X is within X's bound.
    void readonlyReceiver(@Readonly Issue2516Box<X> this) {
        // :: error: (assignment.type.incompatible)
        @ReceiverDependentMutable Object fromField = this.f;
        // Through a @Readonly receiver, the bound of X is @MutabilityLost Object.
        // :: error: (assignment.type.incompatible)
        @ReceiverDependentMutable Object fromItem = this.item;
        // :: error: (assignment.type.incompatible)
        @ReceiverDependentMutable Object implicitItem = item;
        @Readonly Object readonly = this.item;
    }

    void receiverDependentReceiver() {
        @ReceiverDependentMutable Object fromField = this.f;
        @ReceiverDependentMutable Object fromItem = this.item;
    }

    void mutableReceiver(@Mutable Issue2516Box<X> this) {
        @Mutable Object fromItem = this.item;
    }

    <M extends X> void takeX(@Readonly Issue2516Box<X> this, M m) {}

    void callTakeX(@Readonly Issue2516Box<X> this, X x) {
        // M's bound X, adapted through the @Readonly receiver, has bound @MutabilityLost Object.
        // :: error: (mutability.lost.in.bounds)
        this.takeX(x);
    }
}

@ReceiverDependentMutable class Issue2516MutableBound<X extends @Mutable Object> {
    X item;

    Issue2516MutableBound(X item) {
        this.item = item;
    }

    // In a @ReadonlyState method, a @Mutable member read through a non-mutable receiver is lost,
    // and so is a type variable's @Mutable bound.
    @ReadonlyState
    // :: error: (method.mode.signature.mutable)
    void readonlyState(@Readonly Issue2516MutableBound<X> this) {
        // :: error: (assignment.type.incompatible)
        @Mutable Object fromItem = this.item;
        @Readonly Object readonly = this.item;
    }
}
