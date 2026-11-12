import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.Assignable;
import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.MutabilityLost;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;
import org.checkerframework.checker.mutability.qual.TransitiveState;

// Rocq: gait-organized/DeepBoundProbe2.v (a type argument must be below its bound in every mode).
// In @Mutable Box2<@Readonly K, @Mutable Cell> the bound of E reads @Readonly D<@Mutable Cell>.
// K extends D<@Mutable Cell>. In abstract-state code a @Readonly K is a @Readonly D<@Mutable Cell>,
// but in readonly-state and transitive-state code it is only a @Readonly D<@MutabilityLost Cell>.
// So the type is not well formed anywhere, in any mode.
@ReceiverDependentMutable class Dbp2Cell {
    @Assignable @ReceiverDependentMutable Dbp2Cell cache;
    @Readonly Object value;

    Dbp2Cell(@ReceiverDependentMutable Dbp2Cell cache, @Readonly Object value) {
        this.cache = cache;
        this.value = value;
    }
}

@ReceiverDependentMutable class Dbp2D<X extends @Readonly Object> {
    X f;

    Dbp2D(X f) {
        this.f = f;
    }
}

@ReceiverDependentMutable class Dbp2K extends Dbp2D<@Mutable Dbp2Cell> {
    Dbp2K(@Mutable Dbp2Cell f) {
        super(f);
    }
}

@ReceiverDependentMutable class Dbp2Box2<E extends @Readonly Dbp2D<X>, X extends @ReceiverDependentMutable Dbp2Cell> {
    @Assignable E item;
    @ReceiverDependentMutable Dbp2Cell hold;

    Dbp2Box2(E item, @ReceiverDependentMutable Dbp2Cell hold) {
        this.item = item;
        this.hold = hold;
    }

    // wf_mix (well typed in the calculus): E used at its bound, which mentions X, and X used at
    // its @ReceiverDependentMutable bound.
    @ReadonlyState
    @Readonly Object mix(@ReceiverDependentMutable Dbp2Box2<E, X> this) {
        @Readonly Dbp2D<X> d = this.item;
        X x = d.f;
        @ReceiverDependentMutable Dbp2Cell yy = x;
        @ReceiverDependentMutable Dbp2Cell h = this.hold;
        yy.cache = h;
        return yy;
    }
}

@ReceiverDependentMutable class Dbp2Owner {
    // bx_field_not_wf, MutBox_not_wf, MutBox_not_wf_use_Owner, bound_check_fails_Owner,
    // OwnerDef_not_wf, probe2_ct_not_wf: the field type fails the bound check of E in
    // readonly-state and transitive-state mode, so the field is not well formed.
    // NOT REJECTED BY CHECKER
    @Mutable Dbp2Box2<@Readonly Dbp2K, @Mutable Dbp2Cell> bx;

    Dbp2Owner(@Mutable Dbp2Box2<@Readonly Dbp2K, @Mutable Dbp2Cell> bx) {
        this.bx = bx;
    }

    // wf_attack, step_a_typed (well typed in the calculus): rb = this.bx in readonly-state code.
    // The field type read through the @ReceiverDependentMutable receiver is
    // @MutabilityLost Box2<@Readonly K, @MutabilityLost Cell>, a subtype of the type of rb.
    // WELL TYPED IN ROCQ BUT REJECTED BY CHECKER: the checker rejects the local rb.
    @ReadonlyState
    @Readonly Object attack(@ReceiverDependentMutable Dbp2Owner this, @Readonly Object y) {
        // :: error: (mutability.lost.type.argument) :: error: (mutability.lost.lhs)
        @Readonly Dbp2Box2<@Readonly Dbp2K, @MutabilityLost Dbp2Cell> rb = this.bx;
        return y;
    }

    // step_c_field_write_typed (well typed in the calculus): through rb the field item reads
    // @Readonly K, and a @Mutable K is a subtype of it. rb is a parameter here, so that this
    // write is checked apart from the rejected local above.
    // WELL TYPED IN ROCQ BUT REJECTED BY CHECKER: the checker reads item as
    // @MutabilityLost D, not @Readonly K.
    @ReadonlyState
    @Readonly Object writeItem(
            @ReceiverDependentMutable Dbp2Owner this,
            // :: error: (mutability.lost.type.argument)
            @Readonly Dbp2Box2<@Readonly Dbp2K, @MutabilityLost Dbp2Cell> rb,
            @Readonly Object y) {
        @Mutable Dbp2Cell c0 = new @Mutable Dbp2Cell(null, y);
        @Mutable Dbp2K v = new @Mutable Dbp2K(c0);
        // Well typed in Rocq. The checker's result depends on the compilation order: it rejects
        // this write (assignment.type.incompatible, mutability.lost.lhs) when this file is the
        // first compilation unit, and accepts it otherwise, as in this test directory.
        rb.item = v;
        return y;
    }
}

class Dbp2Uses {
    // ROK_not_below_RODMutCell_RS: in readonly-state code @Readonly K is not a subtype of
    // @Readonly D<@Mutable Cell>.
    @ReadonlyState
    void upcastRS(@Readonly Dbp2Uses this, @Readonly Dbp2K k) {
        // :: error: (assignment.type.incompatible)
        @Readonly Dbp2D<@Mutable Dbp2Cell> d = k;
    }

    // ROK_not_below_RODMutCell_TS: the same in transitive-state code.
    @TransitiveState
    void upcastTS(@Readonly Dbp2Uses this, @Readonly Dbp2K k) {
        // :: error: (assignment.type.incompatible)
        @Readonly Dbp2D<@Mutable Dbp2Cell> d = k;
    }

    // ROK_below_RODMutCell_other_modes (contrast, well typed): accepted in abstract-state code.
    @AbstractState
    void upcastAS(@Readonly Dbp2K k) {
        @Readonly Dbp2D<@Mutable Dbp2Cell> d = k;
    }

    // local_MutBox_rejected (and main_untypable): a local of the ill-formed type, in
    // abstract-state code. The lemma holds in every mode.
    // NOT REJECTED BY CHECKER
    @AbstractState
    void localAS(@Mutable Dbp2Box2<@Readonly Dbp2K, @Mutable Dbp2Cell> b) {
        @Mutable Dbp2Box2<@Readonly Dbp2K, @Mutable Dbp2Cell> b0 = b;
    }

    // Contrast: the checker does check the bound of E on this class. An argument that fails it in
    // every mode is rejected.
    @AbstractState
    void boundContrast() {
        // :: error: (type.argument.type.incompatible)
        @Mutable Dbp2Box2<@Readonly Dbp2D<@Immutable Dbp2Cell>, @Mutable Dbp2Cell> b0 = null;
    }

    // local_MutBox_rejected, readonly-state case: the mode of the code where the bound check
    // would fail directly.
    // NOT REJECTED BY CHECKER
    @ReadonlyState
    void localRS(@Readonly Dbp2Uses this) {
        @Mutable Dbp2Box2<@Readonly Dbp2K, @Mutable Dbp2Cell> b0 = null;
    }

    // alloc_MutBox_rejected (and main_untypable): the allocation from main, in abstract-state code.
    // NOT REJECTED BY CHECKER
    @AbstractState
    void allocAS(@Readonly Dbp2K nk, @Mutable Dbp2Cell s0) {
        Object o = new @Mutable Dbp2Box2<@Readonly Dbp2K, @Mutable Dbp2Cell>(nk, s0);
    }
}
