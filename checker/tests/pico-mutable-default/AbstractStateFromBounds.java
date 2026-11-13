import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// Abstract state is classified from type-parameter bounds, not from the caller's type argument
// (GAIT paper, Sections 2.2 and 5.2). A plain use E substitutes the whole supplied argument. With a
// @ReceiverDependentMutable bound, an @Immutable box admits only @Immutable elements, so the
// element
// is part of the box's abstract state. With a @Readonly bound, an @Immutable box may hold a
// @Mutable
// element, which stays independently mutable payload. In both cases the slot itself is not
// assignable through an @Immutable box.
@ReceiverDependentMutable class AsPerson {
    int age;
}

@ReceiverDependentMutable class AsCoupledBox<E extends @ReceiverDependentMutable AsPerson> {
    E element;

    AsCoupledBox(E element) {
        this.element = element;
    }
}

@ReceiverDependentMutable class AsPayloadBox<E extends @Readonly AsPerson> {
    E element;

    AsPayloadBox(E element) {
        this.element = element;
    }
}

class AbstractStateFromBounds {
    void coupled(@Immutable AsCoupledBox<@Immutable AsPerson> b) {
        @Immutable AsPerson p = b.element;
        // :: error: (illegal.field.write)
        b.element = p;
        // :: error: (illegal.field.write)
        b.element.age = 1;
    }

    void coupledRejectsMutableElement(
            // :: error: (type.argument.type.incompatible)
            @Immutable AsCoupledBox<@Mutable AsPerson> b) {}

    void payloadKeepsSuppliedArgument(@Immutable AsPayloadBox<@Mutable AsPerson> b) {
        @Mutable AsPerson p = b.element;
        b.element.age = 1;
        // :: error: (illegal.field.write)
        b.element = p;
    }

    void mutableCoupledBox(@Mutable AsCoupledBox<@Mutable AsPerson> b) {
        @Mutable AsPerson p = b.element;
        b.element = p;
        b.element.age = 1;
    }
}
