import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.ConcreteState;
import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;
import org.checkerframework.checker.mutability.qual.TransitiveState;

// Type-argument bounds hold in every method mode (GAIT paper, Section 3.4). A
// @ReceiverDependentMutable bound couples the element to the container, so an @Immutable container
// rejects a @Mutable element. A @Readonly bound makes the element independent payload, so the same
// argument is admitted. Each mode checks the same uses the same way.
@ReceiverDependentMutable class BoundPerson {}

@ReceiverDependentMutable class BoundCoupledBox<E extends @ReceiverDependentMutable BoundPerson> {}

@ReceiverDependentMutable class BoundPayloadBox<E extends @Readonly BoundPerson> {}

class MethodModeBounds {
    @AbstractState
    void abstractState() {
        @Immutable BoundCoupledBox<@Immutable BoundPerson> coupled = null;
        // :: error: (type.argument.type.incompatible)
        @Immutable BoundCoupledBox<@Mutable BoundPerson> mismatched = null;
        @Immutable BoundPayloadBox<@Mutable BoundPerson> payload = null;
    }

    @ConcreteState
    void concreteState() {
        @Immutable BoundCoupledBox<@Immutable BoundPerson> coupled = null;
        // :: error: (type.argument.type.incompatible)
        @Immutable BoundCoupledBox<@Mutable BoundPerson> mismatched = null;
        @Immutable BoundPayloadBox<@Mutable BoundPerson> payload = null;
    }

    @ReadonlyState
    void readonlyState(@Readonly MethodModeBounds this) {
        @Immutable BoundCoupledBox<@Immutable BoundPerson> coupled = null;
        // :: error: (type.argument.type.incompatible)
        @Immutable BoundCoupledBox<@Mutable BoundPerson> mismatched = null;
        @Immutable BoundPayloadBox<@Mutable BoundPerson> payload = null;
    }

    @TransitiveState
    void transitiveState(@Readonly MethodModeBounds this) {
        @Immutable BoundCoupledBox<@Immutable BoundPerson> coupled = null;
        // :: error: (type.argument.type.incompatible)
        @Immutable BoundCoupledBox<@Mutable BoundPerson> mismatched = null;
        @Immutable BoundPayloadBox<@Mutable BoundPerson> payload = null;
    }
}
