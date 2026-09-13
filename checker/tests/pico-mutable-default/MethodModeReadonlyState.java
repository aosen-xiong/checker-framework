import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.ConcreteState;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.TransitiveState;

// readonly-state and transitive-state change one value-adaptation rule: a declared @Mutable member
// read through a
// non-@Mutable receiver adapts to @MutabilityLost. abstract-state and concrete-state keep the
// ordinary rule, and the
// receiver position is adapted the same way in every mode.
//
// An readonly-state or transitive-state method may not have a @Mutable receiver or parameter, so
// the readonly-state and transitive-state methods below
// declare @Readonly receivers. Two methods keep a @Mutable parameter on purpose, to test adaptation
// through a @Mutable reference, and expect method.mode.signature.mutable for it. The called methods
// have no mode annotation, so they are abstract-state, and each call from a readonly-state method
// also expects method.mode.call.invalid.
@Mutable class ModeRep {
    void clear(@Mutable ModeRep this) {}
}

@Mutable class ModeStore {
    @Mutable ModeRep rep;
    @Mutable ModeRep alias;

    ModeStore(@Mutable ModeRep rep) {
        this.rep = rep;
        this.alias = rep;
    }

    @Mutable ModeRep getAlias(@Readonly ModeStore this) {
        return alias;
    }
}

class MethodModeReadonlyState {
    @AbstractState
    void inspectAS(@Readonly ModeStore s) {
        // abstract-state: a readonly root can recover a mutable reference.
        @Mutable ModeRep r = s.alias;
        r.clear();
    }

    @ReadonlyState
    void inspectRS(@Readonly MethodModeReadonlyState this, @Readonly ModeStore s) {
        // :: error: (assignment.type.incompatible)
        @Mutable ModeRep r = s.alias;
        @Readonly ModeRep q = s.alias;
    }

    @TransitiveState
    void inspectTS(@Readonly MethodModeReadonlyState this, @Readonly ModeStore s) {
        // :: error: (assignment.type.incompatible)
        @Mutable ModeRep r = s.alias;
    }

    @ConcreteState
    void inspectCS(@Readonly ModeStore s) {
        // concrete-state changes only assignability, so the read is as in abstract-state.
        @Mutable ModeRep r = s.alias;
    }

    @ReadonlyState
    void throughMutableReceiver(
            @Readonly MethodModeReadonlyState this,
            // :: error: (method.mode.signature.mutable)
            @Mutable ModeStore s) {
        // A @Mutable receiver keeps the declared @Mutable member.
        @Mutable ModeRep r = s.alias;
    }

    @ReadonlyState
    void methodReturn(@Readonly MethodModeReadonlyState this, @Readonly ModeStore s) {
        // The same rule applies to a declared @Mutable return type.
        // :: error: (assignment.type.incompatible) :: error: (method.mode.call.invalid)
        @Mutable ModeRep r = s.getAlias();
    }

    @AbstractState
    void methodReturnAS(@Readonly ModeStore s) {
        // An abstract-state call of the same method on the same receiver type may use the
        // method-type cache.
        @Mutable ModeRep r = s.getAlias();
    }

    @ReadonlyState
    void methodReturnAfterAS(@Readonly MethodModeReadonlyState this, @Readonly ModeStore s) {
        // An readonly-state call after an abstract-state one must not reuse the abstract-state
        // method type.
        // :: error: (assignment.type.incompatible) :: error: (method.mode.call.invalid)
        @Mutable ModeRep r = s.getAlias();
    }

    // Parameters declared in different methods get receiver types that hash differently, so the
    // two calls above do not share a method-type cache entry. Calls on the same field do.
    @Readonly ModeStore store;

    @AbstractState
    void fieldReturnAS() {
        @Mutable ModeRep r = store.getAlias();
    }

    @ReadonlyState
    void fieldReturnAfterAS(@Readonly MethodModeReadonlyState this) {
        // :: error: (assignment.type.incompatible) :: error: (method.mode.call.invalid)
        @Mutable ModeRep r = store.getAlias();
    }

    @ReadonlyState
    void fieldReturnBeforeAS(@Readonly MethodModeReadonlyState this) {
        // :: error: (assignment.type.incompatible) :: error: (method.mode.call.invalid)
        @Mutable ModeRep r = store.getAlias();
    }

    @AbstractState
    void fieldReturnAfterRS() {
        @Mutable ModeRep r = store.getAlias();
    }

    @ReadonlyState
    void receiverNotScoped(
            @Readonly MethodModeReadonlyState this,
            // :: error: (method.mode.signature.mutable)
            @Mutable ModeRep r) {
        // The declared @Mutable receiver of clear() is not lost through a @Mutable call site.
        // clear() has no mode annotation, so it is abstract-state and cannot be called here.
        // :: error: (method.mode.call.invalid)
        r.clear();
    }
}
