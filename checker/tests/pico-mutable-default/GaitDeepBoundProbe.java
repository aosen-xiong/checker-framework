import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.Assignable;
import org.checkerframework.checker.mutability.qual.ConcreteState;
import org.checkerframework.checker.mutability.qual.MutabilityLost;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;
import org.checkerframework.checker.mutability.qual.TransitiveState;

// Rocq: gait-organized/DeepBoundProbe.v (mode-aware object creation).
// K extends D<@Mutable Cell>, so the first field of a K is a @Mutable Cell. In readonly-state and
// transitive-state code that field, read through an @ReceiverDependentMutable allocation, is a
// @MutabilityLost Cell, so new @ReceiverDependentMutable K(c0, s) with a @Mutable c0 is rejected.
// The calculus initialises fields positionally. Here each constructor takes one parameter per
// field, typed as the field is seen in that class.
@ReceiverDependentMutable class DbpCell {
    @Assignable @ReceiverDependentMutable DbpCell cache;
    @Readonly Object value;

    DbpCell(@ReceiverDependentMutable DbpCell cache, @Readonly Object value) {
        this.cache = cache;
        this.value = value;
    }
}

@ReceiverDependentMutable class DbpD<X extends @Readonly Object> {
    X f;
    @ReceiverDependentMutable DbpCell prot;

    DbpD(X f, @ReceiverDependentMutable DbpCell prot) {
        this.f = f;
        this.prot = prot;
    }
}

@ReceiverDependentMutable class DbpK extends DbpD<@Mutable DbpCell> {
    DbpK(@Mutable DbpCell f, @ReceiverDependentMutable DbpCell prot) {
        super(f, prot);
    }
}

@ReceiverDependentMutable class DbpBox3<
        E extends @ReceiverDependentMutable DbpD<X>, X extends @ReceiverDependentMutable DbpCell> {
    @Assignable E e1;

    DbpBox3(E e1) {
        this.e1 = e1;
    }

    // wf_mix (well typed in the calculus): both type parameters used at their bounds in a
    // readonly-state method, and an @Assignable field of an @ReceiverDependentMutable Cell written.
    // WELL TYPED IN ROCQ BUT REJECTED BY CHECKER: reading a field of type E, whose bound is the
    // generic @ReceiverDependentMutable D<X>, gives a type the checker does not accept as its own
    // bound. The same happens in abstract-state code and with a ground bound.
    @ReadonlyState
    @Readonly Object mix(@ReceiverDependentMutable DbpBox3<E, X> this) {
        // :: error: (assignment.type.incompatible)
        @ReceiverDependentMutable DbpD<X> d = this.e1;
        X x = d.f;
        @ReceiverDependentMutable DbpCell yy = x;
        @ReceiverDependentMutable DbpCell h = d.prot;
        yy.cache = h;
        return yy;
    }
}

@ReceiverDependentMutable class DbpFactory {
    DbpFactory() {}

    // ROBoxT_not_wf: @Readonly Box3<@ReceiverDependentMutable K, @MutabilityLost Cell> is not a
    // well-formed type. Seen through the @Readonly head, the bound of E is
    // @MutabilityLost D<@MutabilityLost Cell>, and @ReceiverDependentMutable K is not below it.
    // The file header still calls this type well formed, but the lemma proves otherwise, and no
    // wf_class lemma exists for Factory.
    @ReadonlyState
    // :: error: (type.argument.type.incompatible) :: error: (mutability.lost.type.argument)
    @Readonly DbpBox3<@ReceiverDependentMutable DbpK, @MutabilityLost DbpCell> make(
            @ReceiverDependentMutable DbpFactory this) {
        return null;
    }
}

@ReceiverDependentMutable class DbpOwner {
    @ReceiverDependentMutable DbpFactory fac;
    @ReceiverDependentMutable DbpCell secret;

    DbpOwner(@ReceiverDependentMutable DbpFactory fac, @ReceiverDependentMutable DbpCell secret) {
        this.fac = fac;
        this.secret = secret;
    }

    // alloc_RDM_K_rejected, alloc_RDM_K_rejected_in_attack, attack_body_untypable,
    // attack_not_wf, OwnerDef_not_wf, probe_ct_not_wf: inside the readonly-state method attack,
    // the first initialiser of an @ReceiverDependentMutable K is checked against
    // @MutabilityLost Cell (K_first_field_read_readonly_state), and a @Mutable Cell is not
    // assignable to it (MutCell_not_assignable_to_LostCell). Before the change, attack stored v
    // into a box, mix wrote c0.cache, and attack then changed the protected secret.
    @ReadonlyState
    @Readonly Object attack(@ReceiverDependentMutable DbpOwner this, @Readonly Object y) {
        @Mutable DbpCell c0 = new @Mutable DbpCell(null, y);
        @ReceiverDependentMutable DbpCell s = this.secret;
        // :: error: (argument.type.incompatible) :: error: (mutability.lost.parameter)
        @ReceiverDependentMutable DbpK v = new @ReceiverDependentMutable DbpK(c0, s);
        return y;
    }

    // alloc_RDM_K_rejected, transitive-state case (K_first_field_read_transitive_state).
    @TransitiveState
    @Readonly Object attackTS(@ReceiverDependentMutable DbpOwner this, @Readonly Object y) {
        @Mutable DbpCell c0 = new @Mutable DbpCell(null, y);
        @ReceiverDependentMutable DbpCell s = this.secret;
        // :: error: (argument.type.incompatible) :: error: (mutability.lost.parameter)
        @ReceiverDependentMutable DbpK v = new @ReceiverDependentMutable DbpK(c0, s);
        return y;
    }

    // Contrast (K_first_field_read_abstract_state): in abstract-state and concrete-state code the
    // first field stays @Mutable Cell, so the same allocation is accepted.
    @AbstractState
    @Readonly Object allocAS(@ReceiverDependentMutable DbpOwner this, @Readonly Object y) {
        @Mutable DbpCell c0 = new @Mutable DbpCell(null, y);
        @ReceiverDependentMutable DbpCell s = this.secret;
        @ReceiverDependentMutable DbpK v = new @ReceiverDependentMutable DbpK(c0, s);
        return y;
    }

    @ConcreteState
    @Readonly Object allocCS(@ReceiverDependentMutable DbpOwner this, @Readonly Object y) {
        @Mutable DbpCell c0 = new @Mutable DbpCell(null, y);
        @ReceiverDependentMutable DbpCell s = this.secret;
        @ReceiverDependentMutable DbpK v = new @ReceiverDependentMutable DbpK(c0, s);
        return y;
    }

    // alloc_Mut_K_into_RDM_var_rejected (with MutK_not_RDMK): a @Mutable K is not an
    // @ReceiverDependentMutable K, in any mode.
    @AbstractState
    @Readonly Object allocMutIntoRdm(@ReceiverDependentMutable DbpOwner this, @Readonly Object y) {
        @Mutable DbpCell c0 = new @Mutable DbpCell(null, y);
        @Mutable DbpCell c1 = new @Mutable DbpCell(null, y);
        // :: error: (assignment.type.incompatible)
        @ReceiverDependentMutable DbpK v = new @Mutable DbpK(c0, c1);
        return y;
    }

    // alloc_Mut_K_with_RDM_secret_rejected (with K_second_field_read_through_Mut and
    // RDMCell_not_MutCell): the second field of a @Mutable K is a @Mutable Cell, and the
    // @ReceiverDependentMutable secret is not one.
    @AbstractState
    @Readonly Object allocMutWithRdmSecret(@ReceiverDependentMutable DbpOwner this, @Readonly Object y) {
        @Mutable DbpCell c0 = new @Mutable DbpCell(null, y);
        @ReceiverDependentMutable DbpCell s = this.secret;
        // :: error: (argument.type.incompatible)
        @Mutable DbpK v = new @Mutable DbpK(c0, s);
        return y;
    }

    // alloc_Mut_K_typed_readonly_state (well typed): a @Mutable K with two @Mutable Cells can be
    // allocated in readonly-state code.
    @ReadonlyState
    @Readonly Object allocMutRS(@ReceiverDependentMutable DbpOwner this, @Readonly Object y) {
        @Mutable DbpCell c0 = new @Mutable DbpCell(null, y);
        @Mutable DbpCell c1 = new @Mutable DbpCell(null, y);
        @Mutable DbpK v = new @Mutable DbpK(c0, c1);
        return y;
    }
}

class DbpWrites {
    // write_Mut_K_into_e1_rejected (with MutK_not_RDMK): through
    // @Readonly Box3<@ReceiverDependentMutable K, @MutabilityLost Cell> the field e1 reads
    // @ReceiverDependentMutable K, and a @Mutable K is not one.
    @AbstractState
    // The parameter type is the ill-formed type of ROBoxT_not_wf, reported again here. The
    // checker reads e1 as @MutabilityLost K (not @ReceiverDependentMutable K) and rejects the
    // write.
    void writeE1(
            // :: error: (type.argument.type.incompatible) :: error: (mutability.lost.type.argument)
            @Readonly DbpBox3<@ReceiverDependentMutable DbpK, @MutabilityLost DbpCell> rb,
            @Mutable DbpK w) {
        // :: error: (assignment.type.incompatible) :: error: (mutability.lost.lhs)
        rb.e1 = w;
    }
}
