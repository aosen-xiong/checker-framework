import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.Assignable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// Rocq: gait-organized/ExamplesDeepBound.v. The file proves one well-typed program (deep_ct_wf,
// main_typed) and proves no program ill typed. One bound mentions another type parameter, a
// second bound is receiver-dependent, and both parameters are used at their bounds inside a
// readonly-state method. The program is translated to check that the checker accepts it.
@ReceiverDependentMutable class EdbCell {
    @Assignable @ReceiverDependentMutable EdbCell cache;
    @Readonly Object value;

    EdbCell(@ReceiverDependentMutable EdbCell cache, @Readonly Object value) {
        this.cache = cache;
        this.value = value;
    }
}

@ReceiverDependentMutable class EdbD<X extends @Readonly Object> {
    X f;

    EdbD(X f) {
        this.f = f;
    }
}

@ReceiverDependentMutable class EdbBox2<E extends @Readonly EdbD<X>, X extends @ReceiverDependentMutable EdbCell> {
    @Assignable E item;

    EdbBox2(E item) {
        this.item = item;
    }

    // wf_first (well typed).
    @ReadonlyState
    @ReceiverDependentMutable EdbCell first(@ReceiverDependentMutable EdbBox2<E, X> this) {
        @Readonly EdbD<X> d = this.item;
        X x = d.f;
        @ReceiverDependentMutable EdbCell yy = x;
        return yy;
    }
}

@ReceiverDependentMutable class EdbRunner {
    EdbRunner() {}

    // wf_run, run_body_typed (well typed): through the mutable box, first returns a @Mutable Cell.
    @ReadonlyState
    @Readonly Object run(@Readonly EdbRunner this, @Readonly Object y) {
        @Mutable EdbCell c = new @Mutable EdbCell(null, y);
        @Mutable EdbD<@Mutable EdbCell> d0 = new @Mutable EdbD<@Mutable EdbCell>(c);
        @Mutable EdbBox2<@Readonly EdbD<@Mutable EdbCell>, @Mutable EdbCell> b =
                new @Mutable EdbBox2<@Readonly EdbD<@Mutable EdbCell>, @Mutable EdbCell>(d0);
        @Mutable EdbCell r = b.first();
        r.value = this;
        return y;
    }

    // main_typed (well typed).
    @AbstractState
    @Readonly Object main(@Readonly EdbRunner this, @Mutable Object y0) {
        @Mutable EdbCell s0 = new @Mutable EdbCell(null, y0);
        @Mutable EdbD<@Mutable EdbCell> pd = new @Mutable EdbD<@Mutable EdbCell>(s0);
        @Mutable EdbBox2<@Readonly EdbD<@Mutable EdbCell>, @Mutable EdbCell> pb =
                new @Mutable EdbBox2<@Readonly EdbD<@Mutable EdbCell>, @Mutable EdbCell>(pd);
        @Mutable EdbRunner rn = new @Mutable EdbRunner();
        return rn.run(y0);
    }
}
