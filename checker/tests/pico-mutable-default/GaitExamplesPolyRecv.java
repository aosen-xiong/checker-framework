import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.PolyMutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// Rocq: gait-organized/ExamplesPolyRecv.v. A qualifier parameter Q on a type argument of a
// declared receiver: [Q]E is written @PolyMutable E. The file proves one well-typed program
// (ct_wf, main_typed) and proves no program ill typed. Java has no explicit qualifier arguments
// (b.<@Mut>get() in the calculus), so the checker infers Q from the receiver.
@ReceiverDependentMutable class PrPerson {
    @Readonly Object name;

    PrPerson(@Readonly Object name) {
        this.name = name;
    }
}

@ReceiverDependentMutable class PrBox<E extends @Readonly PrPerson> {
    E item;

    PrBox(E item) {
        this.item = item;
    }

    // wf_get (well typed). WELL TYPED IN ROCQ BUT REJECTED BY CHECKER: the field read through the
    // receiver @Readonly Box<@PolyMutable E> is not seen as @PolyMutable E. The same errors occur
    // when get is abstract-state.
    @ReadonlyState
    @PolyMutable E get(@Readonly PrBox<@PolyMutable E> this) {
        // :: error: (assignment.type.incompatible)
        @PolyMutable E r = this.item;
        return r;
    }
}

class PrMain {
    // main_typed (well typed), with the qualifier arguments inferred. WELL TYPED IN ROCQ BUT
    // REJECTED BY CHECKER: the checker does not resolve @PolyMutable from the receiver's type
    // argument, so neither call is accepted.
    @AbstractState
    void main(@Mutable PrMain this) {
        @Mutable PrPerson p = new @Mutable PrPerson(this);
        @Immutable PrPerson i = new @Immutable PrPerson(this);
        @Mutable PrBox<@Mutable PrPerson> b = new @Mutable PrBox<@Mutable PrPerson>(p);
        @Immutable PrBox<@Immutable PrPerson> c = new @Immutable PrBox<@Immutable PrPerson>(i);
        // :: error: (assignment.type.incompatible) :: error: (method.invocation.invalid)
        @Mutable PrPerson m = b.get();
        // :: error: (assignment.type.incompatible) :: error: (method.invocation.invalid)
        @Immutable PrPerson j = c.get();
    }

    // recv_at_imm_on_mut_box (an Example and its comment, no negative lemma): b.<@Imm>get() on a
    // box of mutable persons asks for a box of immutable persons, which b is not. The explicit
    // qualifier argument has no Java form. The nearest Java program asks for an @Immutable result
    // from the mutable box. It is rejected, but with the same errors as the well-typed calls in
    // main above, so the rejection shows nothing about this program.
    @AbstractState
    void immOnMutBox(@Mutable PrMain this) {
        @Mutable PrPerson p = new @Mutable PrPerson(this);
        @Mutable PrBox<@Mutable PrPerson> b = new @Mutable PrBox<@Mutable PrPerson>(p);
        // :: error: (assignment.type.incompatible) :: error: (method.invocation.invalid)
        @Immutable PrPerson j = b.get();
    }
}
