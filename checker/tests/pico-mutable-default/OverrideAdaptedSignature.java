import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// Overriding compares signatures after adapting the inherited signature at the overriding class's
// mutability bound (GAIT paper, Section 3.4): a @ReceiverDependentMutable qualifier becomes the
// subclass's bound. Results are covariant, and parameters and type-parameter bounds are
// contravariant.
@ReceiverDependentMutable class OvBase {
    @ReceiverDependentMutable Object get(@Readonly OvBase this) {
        return null;
    }

    @Immutable Object getImmutable(@Readonly OvBase this) {
        return null;
    }

    void put(@Readonly OvBase this, @Immutable Object o) {}

    void putReadonly(@Readonly OvBase this, @Readonly Object o) {}

    <T extends @Immutable Object> void bounded(@Readonly OvBase this, T t) {}

    <T extends @Readonly Object> void boundedReadonly(@Readonly OvBase this, T t) {}
}

@Mutable class OvMutableSub extends OvBase {
    // The inherited @ReceiverDependentMutable result is @Mutable at this class's bound.
    @Override
    @Mutable Object get(@Readonly OvMutableSub this) {
        return null;
    }

    @Override
    // :: error: (override.return.invalid)
    @Readonly Object getImmutable(@Readonly OvMutableSub this) {
        return null;
    }

    @Override
    void put(@Readonly OvMutableSub this, @Readonly Object o) {}

    @Override
    void putReadonly(
            @Readonly OvMutableSub this,
            // :: error: (override.param.invalid)
            @Immutable Object o) {}

    @Override
    <T extends @Readonly Object> void bounded(@Readonly OvMutableSub this, T t) {}

    // A narrower type-parameter bound is rejected, and the parameter of that type no longer
    // matches.
    @Override
    // :: error: (override.typaram.invalid) :: error: (override.param.invalid)
    <T extends @Immutable Object> void boundedReadonly(@Readonly OvMutableSub this, T t) {}
}

@Immutable class OvImmutableSub extends OvBase {
    // The inherited @ReceiverDependentMutable result is @Immutable at this class's bound.
    @Override
    @Immutable Object get(@Readonly OvImmutableSub this) {
        return null;
    }
}

@Mutable class OvWrongBoundSub extends OvBase {
    // At a @Mutable bound the inherited result is @Mutable, which @Immutable does not refine.
    @Override
    // :: error: (override.return.invalid)
    @Immutable Object get(@Readonly OvWrongBoundSub this) {
        return null;
    }
}
