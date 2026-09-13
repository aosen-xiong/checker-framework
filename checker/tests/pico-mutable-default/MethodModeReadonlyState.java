import org.checkerframework.checker.mutability.qual.AS;
import org.checkerframework.checker.mutability.qual.CS;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.RS;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.TS;

// RS and TS change one value-adaptation rule: a declared @Mutable member read through a
// non-@Mutable receiver adapts to @MutabilityLost. AS and CS keep the ordinary rule, and the
// receiver position is adapted the same way in every mode.
//
// An RS or TS method may not have a @Mutable receiver or parameter, so the RS and TS methods below
// declare @Readonly receivers. Two methods keep a @Mutable parameter on purpose, to test adaptation
// through a @Mutable reference, and expect method.mode.signature.mutable for it.
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
    @AS
    void inspectAS(@Readonly ModeStore s) {
        // AS: a readonly root can recover a mutable reference.
        @Mutable ModeRep r = s.alias;
        r.clear();
    }

    @RS
    void inspectRS(@Readonly MethodModeReadonlyState this, @Readonly ModeStore s) {
        // :: error: (assignment.type.incompatible)
        @Mutable ModeRep r = s.alias;
        @Readonly ModeRep q = s.alias;
    }

    @TS
    void inspectTS(@Readonly MethodModeReadonlyState this, @Readonly ModeStore s) {
        // :: error: (assignment.type.incompatible)
        @Mutable ModeRep r = s.alias;
    }

    @CS
    void inspectCS(@Readonly ModeStore s) {
        // CS changes only assignability, so the read is as in AS.
        @Mutable ModeRep r = s.alias;
    }

    @RS
    void throughMutableReceiver(
            @Readonly MethodModeReadonlyState this,
            // :: error: (method.mode.signature.mutable)
            @Mutable ModeStore s) {
        // A @Mutable receiver keeps the declared @Mutable member.
        @Mutable ModeRep r = s.alias;
    }

    @RS
    void methodReturn(@Readonly MethodModeReadonlyState this, @Readonly ModeStore s) {
        // The same rule applies to a declared @Mutable return type.
        // :: error: (assignment.type.incompatible)
        @Mutable ModeRep r = s.getAlias();
    }

    @AS
    void methodReturnAS(@Readonly ModeStore s) {
        // An AS call of the same method on the same receiver type may use the method-type cache.
        @Mutable ModeRep r = s.getAlias();
    }

    @RS
    void methodReturnAfterAS(@Readonly MethodModeReadonlyState this, @Readonly ModeStore s) {
        // An RS call after an AS one must not reuse the AS method type.
        // :: error: (assignment.type.incompatible)
        @Mutable ModeRep r = s.getAlias();
    }

    // Parameters declared in different methods get receiver types that hash differently, so the
    // two calls above do not share a method-type cache entry. Calls on the same field do.
    @Readonly ModeStore store;

    @AS
    void fieldReturnAS() {
        @Mutable ModeRep r = store.getAlias();
    }

    @RS
    void fieldReturnAfterAS(@Readonly MethodModeReadonlyState this) {
        // :: error: (assignment.type.incompatible)
        @Mutable ModeRep r = store.getAlias();
    }

    @RS
    void fieldReturnBeforeAS(@Readonly MethodModeReadonlyState this) {
        // :: error: (assignment.type.incompatible)
        @Mutable ModeRep r = store.getAlias();
    }

    @AS
    void fieldReturnAfterRS() {
        @Mutable ModeRep r = store.getAlias();
    }

    @RS
    void receiverNotScoped(
            @Readonly MethodModeReadonlyState this,
            // :: error: (method.mode.signature.mutable)
            @Mutable ModeRep r) {
        // The declared @Mutable receiver of clear() is not lost through a @Mutable call site.
        r.clear();
    }
}
