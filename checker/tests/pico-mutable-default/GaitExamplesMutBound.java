import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.Assignable;
import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// Rocq: gait-organized/ExamplesMutBound.v (a class with @Mutable inside the bound of its type
// parameter). The program of the file is well typed and is translated below. The file's only
// rejected program appears in a comment, not in a lemma: a readonly-state method may not
// allocate an @Immutable C, because the bound @Readonly D<@Mutable Cell>, read through the
// method's own adaptation table, is @Readonly D<@MutabilityLost Cell>, which is not lost-free.
// (The lemmas C_bound_has_mut and mutbound_ct_not_bounds_nm are negative facts about a dropped
// side condition, not rejected programs.)
@ReceiverDependentMutable class MbCell {
    @Assignable @Readonly Object v;

    MbCell(@Readonly Object v) {
        this.v = v;
    }
}

@ReceiverDependentMutable class MbD<X extends @Readonly Object> {
    X f;

    MbD(X f) {
        this.f = f;
    }
}

@ReceiverDependentMutable class MbP<Y extends @Readonly Object> {
    Y g;

    MbP(Y g) {
        this.g = g;
    }

    // look (well typed).
    @ReadonlyState
    @Readonly Object look(@Readonly MbP<Y> this) {
        @Readonly Object o = this.g;
        return o;
    }
}

@ReceiverDependentMutable class MbC<X extends @Readonly MbD<@Mutable MbCell>> extends MbP<X> {
    MbC(X g) {
        super(g);
    }
}

@ReceiverDependentMutable class MbRunner {
    MbRunner() {}

    // run (well typed, run_new_ROC_typed and the following lemmas): mutable C objects allocated in
    // readonly-state code, one held through a @Readonly variable, and the inherited look called on
    // both.
    @ReadonlyState
    @Readonly Object run(@Readonly MbRunner this, @Readonly Object y) {
        @Mutable MbCell c = new @Mutable MbCell(y);
        @Mutable MbD<@Mutable MbCell> d0 = new @Mutable MbD<@Mutable MbCell>(c);
        @Readonly MbC<@Readonly MbD<@Mutable MbCell>> bi =
                new @Mutable MbC<@Readonly MbD<@Mutable MbCell>>(d0);
        @Mutable MbC<@Readonly MbD<@Mutable MbCell>> bm =
                new @Mutable MbC<@Readonly MbD<@Mutable MbCell>>(d0);
        @Readonly Object r = bi.look();
        @Readonly Object r2 = bm.look();
        return y;
    }

    // Comment in ExamplesMutBound.v (header and before run_new_ROC_typed), no lemma: the
    // @Immutable allocation is rejected in readonly-state code, since the bound of C read through
    // the method's adaptation table is @Readonly D<@MutabilityLost Cell>.
    @ReadonlyState
    @Readonly Object runImm(@Readonly MbRunner this, @Readonly Object y) {
        @Mutable MbCell c = new @Mutable MbCell(y);
        @Mutable MbD<@Mutable MbCell> d0 = new @Mutable MbD<@Mutable MbCell>(c);
        // The checker rejects this, but not at the bound: it reads the type argument of the new
        // object as @Readonly D<@MutabilityLost Cell>, and so also the constructor parameter.
        @Readonly MbC<@Readonly MbD<@Mutable MbCell>> bi =
                // :: error: (assignment.type.incompatible) :: error: (mutability.lost.parameter)
                new @Immutable MbC<@Readonly MbD<@Mutable MbCell>>(d0);
        return y;
    }

    // main (well typed): the same @Immutable allocation in abstract-state code.
    @AbstractState
    @Readonly Object main(@Readonly MbRunner this, @Readonly Object y) {
        @Mutable MbCell s0 = new @Mutable MbCell(y);
        @Mutable MbD<@Mutable MbCell> pd = new @Mutable MbD<@Mutable MbCell>(s0);
        @Immutable MbC<@Readonly MbD<@Mutable MbCell>> pc =
                new @Immutable MbC<@Readonly MbD<@Mutable MbCell>>(pd);
        @Mutable MbRunner rn = new @Mutable MbRunner();
        return rn.run(y);
    }
}
