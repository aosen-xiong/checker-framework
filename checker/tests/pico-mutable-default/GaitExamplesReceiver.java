import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// Rocq: gait-organized/ExamplesReceiver.v (a declared receiver that requalifies a type parameter
// of its class). The Parent/Child classes of the overriding examples exist only in the Rocq
// comments and lemma statements. They are built here from those comments.
@ReceiverDependentMutable class ErPerson {
    @Readonly Object age;

    ErPerson(@Readonly Object age) {
        this.age = age;
    }
}

@ReceiverDependentMutable class ErROBox<E extends @Readonly ErPerson> {
    E element;

    ErROBox(E element) {
        this.element = element;
    }

    // wf_ageUp (well typed): the body writes the element through the mutable view that the
    // receiver promises.
    @AbstractState
    @Readonly Object ageUp(@Readonly ErROBox<@Mutable E> this, @Readonly Object v) {
        @Mutable E p = this.element;
        p.age = v;
        return v;
    }
}

@ReceiverDependentMutable class ErRSBox<E extends @Readonly ErPerson> {
    ErRSBox() {}

    // wf_peek (well typed): a readonly-state method may requalify a receiver argument as
    // receiver-dependent.
    @ReadonlyState
    @Readonly Object peek(
            @ReceiverDependentMutable ErRSBox<@ReceiverDependentMutable E> this,
            @Readonly Object v) {
        return v;
    }
}

class ErCalls {
    // call_on_mut_accepted (contrast, well typed).
    @AbstractState
    @Readonly Object onMut(@Readonly ErROBox<@Mutable ErPerson> b, @Readonly Object v) {
        return b.ageUp(v);
    }

    // call_on_imm_rejected: the declared receiver asks for a box of mutable persons, and a box of
    // immutable persons is not one, so ageUp cannot reach an immutable person.
    @AbstractState
    @Readonly Object onImm(@Readonly ErROBox<@Immutable ErPerson> b, @Readonly Object v) {
        // :: error: (method.invocation.invalid)
        return b.ageUp(v);
    }

    // rdm_arg_through_ro_rejected: the receiver argument @ReceiverDependentMutable E, seen
    // through a @Readonly reference in abstract-state code, is @MutabilityLost and cannot be
    // checked.
    @AbstractState
    @Readonly Object peekThroughReadonly(@Readonly ErRSBox<@Mutable ErPerson> b, @Readonly Object v) {
        // :: error: (mutability.lost.receiver)
        return b.peek(v);
    }

    // Contrast added here, not a Rocq lemma: through a @Mutable reference the requalified argument
    // adapts to @Mutable, so by ST_Call (member_recv, recv_args_known) the calculus accepts the
    // call. The checker rejects it: with an @ReceiverDependentMutable receiver head it leaves the
    // @ReceiverDependentMutable type argument unadapted. With a @Readonly head it adapts it.
    @AbstractState
    @Readonly Object peekThroughMutable(@Mutable ErRSBox<@Mutable ErPerson> b, @Readonly Object v) {
        // :: error: (method.invocation.invalid)
        return b.peek(v);
    }
}

@ReceiverDependentMutable class ErParent<E extends @Readonly ErPerson> {
    ErParent() {}

    void inspect(@Readonly ErParent<E> this) {}
}

// override_narrowing_rejected, override_narrowing_rejected_by_subtyping: the override asks the
// receiver for a mutable element type, which the inherited method does not. A Child with an
// immutable element type would reach the override through the inherited receiver.
@ReceiverDependentMutable class ErChild<E extends @Readonly ErPerson> extends ErParent<E> {
    ErChild() {}

    @Override
    // :: error: (override.receiver.invalid)
    void inspect(@Readonly ErChild<@Mutable E> this) {}
}

// override_plain_accepted, override_plain_accepted_by_subtyping (contrast, well typed).
@ReceiverDependentMutable class ErPlainChild<E extends @Readonly ErPerson> extends ErParent<E> {
    ErPlainChild() {}

    @Override
    void inspect(@Readonly ErPlainChild<E> this) {}
}

@ReceiverDependentMutable class ErParent2<Z extends @Readonly ErPerson> {
    ErParent2() {}

    void inspect(@Readonly ErParent2<@Mutable Z> this) {}
}

// override_qualified_pass_rejected: both receivers seen as Parent types are
// @Readonly Parent<@Mutable E>, so subtyping holds. But Child passes E to Parent under a written
// qualifier, so every Child<@Immutable Person> is a Parent<@Mutable Person> and the inherited
// receiver accepts it, while the override assumes a mutable element. The premise
// recv_qual_passed rejects the override.
// NOT REJECTED BY CHECKER
@ReceiverDependentMutable class ErChild2<E extends @Readonly ErPerson> extends ErParent2<@Mutable E> {
    ErChild2() {}

    @Override
    void inspect(@Readonly ErChild2<@Mutable E> this) {}
}
