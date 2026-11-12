import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.MutabilityLost;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// Rocq: gait-organized/SuperArgsRegression.v (mode-aware superclass view).
// C extends P<@Mutable Cell>. In readonly-state code a @Readonly C is only a
// @Readonly P<@MutabilityLost Cell>, so nothing read out of it is a @Mutable Cell.
@ReceiverDependentMutable class SarCell {
    @Readonly Object value;

    SarCell(@Readonly Object value) {
        this.value = value;
    }
}

@ReceiverDependentMutable class SarP<E extends @Readonly Object> {
    E item;

    SarP(E item) {
        this.item = item;
    }
}

@ReceiverDependentMutable class SarC extends SarP<@Mutable SarCell> {
    SarC(@Mutable SarCell item) {
        super(item);
    }
}

// CT2 of the Rocq file: P gets a readonly-state getter with a @Readonly receiver.
@ReceiverDependentMutable class SarP2<E extends @Readonly Object> {
    E item;

    SarP2(E item) {
        this.item = item;
    }

    @ReadonlyState
    E get(@Readonly SarP2<E> this) {
        return item;
    }
}

@ReceiverDependentMutable class SarC2 extends SarP2<@Mutable SarCell> {
    SarC2(@Mutable SarCell item) {
        super(item);
    }
}

// CT3 of the Rocq file: the same getter with a @ReceiverDependentMutable receiver.
@ReceiverDependentMutable class SarP3<E extends @Readonly Object> {
    E item;

    SarP3(E item) {
        this.item = item;
    }

    @ReadonlyState
    E getRdm(@ReceiverDependentMutable SarP3<E> this) {
        return item;
    }
}

@ReceiverDependentMutable class SarC3 extends SarP3<@Mutable SarCell> {
    SarC3(@Mutable SarCell item) {
        super(item);
    }
}

@ReceiverDependentMutable class SarTool {
    SarTool() {}

    // upcast_rejected, assignment_rejected, hack_body_untypable, hack_not_wf: in readonly-state
    // code @Readonly C is not a subtype of @Readonly P<@Mutable Cell>, so p = c is rejected.
    // Before the change, p.item was a @Mutable Cell and k.value = y changed a protected field.
    @ReadonlyState
    @Readonly Object hack(@Readonly SarTool this, @Readonly SarC c, @Readonly Object y) {
        @Readonly SarP<@Mutable SarCell> p;
        @Mutable SarCell k;
        // :: error: (assignment.type.incompatible)
        p = c;
        k = p.item;
        k.value = y;
        return y;
    }

    // upcast_accepted_abstract (contrast, well typed): in abstract-state code the upcast holds.
    @AbstractState
    @Readonly Object hackAbstract(@Readonly SarTool this, @Readonly SarC c, @Readonly Object y) {
        @Readonly SarP<@Mutable SarCell> p;
        p = c;
        return y;
    }

    // upcast_gives_lost, item_read_type, lost_cell_not_mut: the upcast to
    // @Readonly P<@MutabilityLost Cell> is accepted, and the item read through it is a
    // @MutabilityLost Cell, which is not a @Mutable Cell. By ST_Local and ST_VarAss (which ask
    // only well-formedness and subtyping) the calculus accepts the local p. No lemma states this.
    // REJECTED BY CHECKER, ACCEPTED BY THE RULES: the checker rejects the local p.
    @ReadonlyState
    @Readonly Object lostView(@Readonly SarTool this, @Readonly SarC c, @Readonly Object y) {
        // :: error: (mutability.lost.type.argument) :: error: (mutability.lost.lhs)
        @Readonly SarP<@MutabilityLost SarCell> p = c;
        // :: error: (assignment.type.incompatible)
        @Mutable SarCell k = p.item;
        return y;
    }

    // get_call_result, get_call_not_mut: the inherited readonly-state getter, called on a
    // @Readonly C in readonly-state code, returns a @MutabilityLost Cell, not a @Mutable Cell.
    @ReadonlyState
    @Readonly Object viaGetter(@Readonly SarTool this, @Readonly SarC2 c, @Readonly Object y) {
        // :: error: (assignment.type.incompatible)
        @Mutable SarCell k = c.get();
        return y;
    }

    // get_rdm_call_result, get_rdm_call_not_mut: the same with a @ReceiverDependentMutable
    // receiver on the getter.
    @ReadonlyState
    @Readonly Object viaRdmGetter(@Readonly SarTool this, @Readonly SarC3 c, @Readonly Object y) {
        // :: error: (assignment.type.incompatible)
        @Mutable SarCell k = c.getRdm();
        return y;
    }
}
