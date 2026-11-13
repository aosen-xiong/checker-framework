import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.ConcreteState;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;
import org.checkerframework.checker.mutability.qual.TransitiveState;

// Rocq: gait-organized/OverrideReturnProbe.v (covariant return of a readonly-state override).
// Sub extends Super<@Mutable Cell> and overrides pick with return type @Readonly C. The overridden
// return type @Readonly P<X> is seen from Sub as @Readonly P<@Mutable Cell>, and the comparison is
// made in Sub.pick's own mode, readonly-state, where a @Readonly C is only a
// @Readonly P<@MutabilityLost Cell>. So the override is rejected at its return type.
@ReceiverDependentMutable class OrpCell {
    @Readonly Object value;

    OrpCell(@Readonly Object value) {
        this.value = value;
    }
}

@ReceiverDependentMutable class OrpP<E extends @Readonly Object> {
    E item;

    OrpP(E item) {
        this.item = item;
    }
}

@ReceiverDependentMutable class OrpC extends OrpP<@Mutable OrpCell> {
    OrpC(@Mutable OrpCell item) {
        super(item);
    }
}

@ReceiverDependentMutable class OrpSuper<X extends @Readonly Object> {
    OrpSuper() {}

    // Super_pick_wf (well typed).
    @ReadonlyState
    @Readonly OrpP<X> pick(@Readonly OrpSuper<X> this, @Readonly OrpC c) {
        return null;
    }
}

@ReceiverDependentMutable class OrpSub extends OrpSuper<@Mutable OrpCell> {
    OrpSub() {}

    // Sub_pick_return_premise_fails, Sub_pick_override_rejected, Sub_pick_not_wf, CT_not_wf:
    // in readonly-state mode @Readonly C is not below @Readonly P<@Mutable Cell>. If accepted,
    // Tool.hack below gets c back as a P<@Mutable Cell> and writes its protected cell.
    @Override
    @ReadonlyState
    // :: error: (override.return.invalid)
    @Readonly OrpC pick(@Readonly OrpSub this, @Readonly OrpC c) {
        return c;
    }
}

// no_return_type_works: no return type rescues the override. With the overridden return type
// itself, the body return c fails instead (upcast_rejected in readonly-state code).
@ReceiverDependentMutable class OrpSubSameReturn extends OrpSuper<@Mutable OrpCell> {
    OrpSubSameReturn() {}

    @Override
    @ReadonlyState
    @Readonly OrpP<@Mutable OrpCell> pick(@Readonly OrpSubSameReturn this, @Readonly OrpC c) {
        // :: error: (return.type.incompatible)
        return c;
    }
}

// Contrast: the checker does compare these return types. A return type that is not below
// @Readonly P<@Mutable Cell> in any mode (type arguments are invariant) is rejected.
@ReceiverDependentMutable class OrpSubWider extends OrpSuper<@Mutable OrpCell> {
    OrpSubWider() {}

    @Override
    @ReadonlyState
    // :: error: (override.return.invalid)
    @Readonly OrpP<@Readonly OrpCell> pick(@Readonly OrpSubWider this, @Readonly OrpC c) {
        return null;
    }
}

// Contrast (return_check_accepted_abstract, override_accepted_if_compared_abstractly): the same
// override between abstract-state methods is accepted.
@ReceiverDependentMutable class OrpSuperAS<X extends @Readonly Object> {
    OrpSuperAS() {}

    @AbstractState
    @Readonly OrpP<X> pick(@Readonly OrpSuperAS<X> this, @Readonly OrpC c) {
        return null;
    }
}

@ReceiverDependentMutable class OrpSubAS extends OrpSuperAS<@Mutable OrpCell> {
    OrpSubAS() {}

    @Override
    @AbstractState
    @Readonly OrpC pick(@Readonly OrpSubAS this, @Readonly OrpC c) {
        return c;
    }
}

@ReceiverDependentMutable class OrpTool {
    OrpTool() {}

    // hack_wf (well typed): through a @Mutable Super<@Mutable Cell> the call returns
    // @Readonly P<@Mutable Cell>, whose item is a @Mutable Cell.
    @ReadonlyState
    @Readonly Object hack(@Readonly OrpTool this, @Readonly OrpC c, @Readonly Object y) {
        @Mutable OrpSub s = new @Mutable OrpSub();
        @Mutable OrpSuper<@Mutable OrpCell> r = s;
        @Readonly OrpP<@Mutable OrpCell> b = r.pick(c);
        @Mutable OrpCell k = b.item;
        k.value = y;
        return y;
    }

    // upcast_rejected, readonly-state case.
    @ReadonlyState
    void upcastRS(@Readonly OrpTool this, @Readonly OrpC c) {
        // :: error: (assignment.type.incompatible)
        @Readonly OrpP<@Mutable OrpCell> p = c;
    }

    // upcast_rejected, transitive-state case.
    @TransitiveState
    void upcastTS(@Readonly OrpTool this, @Readonly OrpC c) {
        // :: error: (assignment.type.incompatible)
        @Readonly OrpP<@Mutable OrpCell> p = c;
    }

    @AbstractState
    void asCallee(@Readonly OrpTool this) {}

    @ConcreteState
    void csCallee(@Readonly OrpTool this) {}

    // weaker_mode_not_callable: an abstract-state or concrete-state method cannot be called from
    // readonly-state or transitive-state code.
    @ReadonlyState
    void rsCallsAS(@Readonly OrpTool this) {
        // :: error: (method.mode.call.invalid)
        this.asCallee();
    }

    @ReadonlyState
    void rsCallsCS(@Readonly OrpTool this) {
        // :: error: (method.mode.call.invalid)
        this.csCallee();
    }

    @TransitiveState
    void tsCallsAS(@Readonly OrpTool this) {
        // :: error: (method.mode.call.invalid)
        this.asCallee();
    }

    @TransitiveState
    void tsCallsCS(@Readonly OrpTool this) {
        // :: error: (method.mode.call.invalid)
        this.csCallee();
    }
}
