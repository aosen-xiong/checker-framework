import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;
import org.checkerframework.checker.mutability.qual.TransitiveState;
import org.checkerframework.dataflow.qual.Pure;
import org.checkerframework.dataflow.qual.SideEffectFree;

// With -AsideEffectFreeAsTransitiveState, a method annotated @SideEffectFree or @Pure and with no
// mode of its own is checked in @TransitiveState. @Pure is not verified, so this is not sound.
@ReceiverDependentMutable class SefData {
    int value;
}

class SideEffectFreeAsTransitiveState {
    int helper(@Readonly SideEffectFreeAsTransitiveState this) {
        return 1;
    }

    @TransitiveState
    int transitiveHelper(@Readonly SideEffectFreeAsTransitiveState this) {
        return 1;
    }

    @SideEffectFree
    int readsOnly(@Readonly SideEffectFreeAsTransitiveState this, @Readonly SefData d) {
        return d.value + transitiveHelper();
    }

    @SideEffectFree
    // :: error: (method.mode.signature.mutable)
    void mutableParameter(@Readonly SideEffectFreeAsTransitiveState this, @Mutable SefData d) {}

    @Pure
    int callsAbstractState(@Readonly SideEffectFreeAsTransitiveState this) {
        // :: error: (method.mode.call.invalid)
        return helper();
    }

    // An explicit mode wins.
    @SideEffectFree
    @ReadonlyState
    int explicitMode(@Readonly SideEffectFreeAsTransitiveState this) {
        return 0;
    }
}
