import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.ConcreteState;
import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;
import org.checkerframework.checker.mutability.qual.TransitiveState;

// A readonly-state or transitive-state method may not cast to a narrower mutability qualifier
// (GAIT paper, Sections 2.3 and 3.5). A non-null value of a class declared @Mutable is mutable, so
// abstract-state and concrete-state may narrow a @Readonly reference to it without a runtime test.
// In readonly-state and transitive-state that narrowing would recover mutable authority, so it is
// rejected, and so is narrowing to @Immutable. Casts up the qualifier order are allowed in every
// mode.
@Mutable class CastMutableOnly {}

@Immutable class CastImmutableOnly {}

@ReceiverDependentMutable class CastRdm {}

class MethodModeCasts {
    @AbstractState
    void abstractStateNarrowsToPinnedMutable(@Readonly CastMutableOnly r) {
        @Mutable CastMutableOnly m = (@Mutable CastMutableOnly) r;
    }

    @ConcreteState
    void concreteStateNarrowsToPinnedMutable(@Readonly CastMutableOnly r) {
        @Mutable CastMutableOnly m = (@Mutable CastMutableOnly) r;
    }

    @ReadonlyState
    void readonlyStateRejectsNarrowingToMutable(
            @Readonly MethodModeCasts this, @Readonly CastMutableOnly r) {
        // :: error: (method.mode.cast.invalid)
        @Readonly Object o = (@Mutable CastMutableOnly) r;
    }

    @TransitiveState
    void transitiveStateRejectsNarrowingToImmutable(
            @Readonly MethodModeCasts this, @Readonly CastImmutableOnly r) {
        // :: error: (method.mode.cast.invalid)
        @Readonly Object o = (@Immutable CastImmutableOnly) r;
    }

    @ReadonlyState
    void readonlyStateRejectsNarrowingReceiverDependentClass(
            @Readonly MethodModeCasts this, @Readonly CastRdm r) {
        // CastRdm is not pinned to @Mutable, so the framework also warns about this cast in every
        // mode.
        // :: error: (method.mode.cast.invalid) :: warning: (cast.unsafe)
        @Readonly Object o = (@Mutable CastRdm) r;
    }

    @ReadonlyState
    void readonlyStateAllowsUpcastAndIdentity(
            @Readonly MethodModeCasts this, @Readonly CastMutableOnly r) {
        @Readonly Object up = (@Readonly Object) r;
        @Readonly CastMutableOnly same = (@Readonly CastMutableOnly) r;
    }

    @ReadonlyState
    void lambdaUsesEnclosingMode(@Readonly MethodModeCasts this, @Readonly CastMutableOnly r) {
        Runnable run =
                () -> {
                    // :: error: (method.mode.cast.invalid)
                    @Readonly Object o = (@Mutable CastMutableOnly) r;
                };
    }
}
