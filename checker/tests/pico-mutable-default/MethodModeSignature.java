import org.checkerframework.checker.mutability.qual.AS;
import org.checkerframework.checker.mutability.qual.CS;
import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.PolyMutable;
import org.checkerframework.checker.mutability.qual.RS;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;
import org.checkerframework.checker.mutability.qual.TS;

// An RS or TS method's receiver and parameters may not mention @Mutable anywhere, including in
// nested type arguments and in the declared bounds of the type variables they use. A bare type
// variable is read through its whole bound. A qualified use such as @Readonly T replaces the head
// of the bound, so only the bound's type arguments are examined. @PolyMutable is checked as poly,
// not as @Mutable. The return type is not checked. AS and CS impose nothing.
@Mutable class SigCell {}

@ReceiverDependentMutable class SigBox<T extends @Readonly Object> {}

@ReceiverDependentMutable class SigSelf<T extends @Readonly SigSelf<T>> {}

@ReceiverDependentMutable class MethodModeSignature {
    @RS
    void readonlyParameter(@Readonly MethodModeSignature this, @Readonly SigCell c) {}

    @RS
    void immutableParameter(@Readonly MethodModeSignature this, @Immutable Object i) {}

    @RS
    void receiverDependentReceiver(@ReceiverDependentMutable MethodModeSignature this) {}

    @RS
    void mutableReceiver(
            // :: error: (method.mode.signature.mutable)
            @Mutable MethodModeSignature this) {}

    @RS
    void mutableParameter(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            @Mutable SigCell c) {}

    @TS
    void mutableParameterTS(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            @Mutable SigCell c) {}

    @RS
    void polyMutable(@PolyMutable MethodModeSignature this, @PolyMutable SigCell c) {}

    @RS
    void nestedMutableArgument(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            @Readonly SigBox<@Mutable SigCell> b) {}

    @RS
    void nestedReadonlyArgument(
            @Readonly MethodModeSignature this, @Readonly SigBox<@Readonly SigCell> b) {}

    @RS
    void mutableArrayComponent(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            @Mutable SigCell @Readonly [] cells) {}

    @RS
    void readonlyArray(@Readonly MethodModeSignature this, @Readonly SigCell @Readonly [] cells) {}

    @RS
    <T> void bareVariableImplicitBound(@Readonly MethodModeSignature this, T t) {}

    @RS
    <T extends @Readonly Object> void bareVariableReadonlyBound(
            @Readonly MethodModeSignature this, T t) {}

    @RS
    <T extends @Mutable SigCell> void bareVariableMutableBound(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            T t) {}

    @RS
    <T extends @Readonly SigBox<@Mutable SigCell>> void qualifiedVariableBoundArgument(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            @Readonly T t) {}

    @RS
    <T extends @Mutable SigBox<@Readonly SigCell>> void qualifiedVariableHidesBoundHead(
            @Readonly MethodModeSignature this, @Readonly T t) {}

    @RS
    <T extends @Readonly Object> void qualifiedMutableVariable(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            @Mutable T t) {}

    @RS
    <T extends @Readonly Object, U extends T> void bareVariableBoundedByVariable(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            U u) {}

    @RS
    <T extends @Readonly SigSelf<T>> void fBoundedVariableTerminates(
            @Readonly MethodModeSignature this, T t) {}

    @RS
    static void staticMethodHasNoReceiver(@Readonly SigCell c) {}

    @RS
    @Mutable SigCell returnTypeIsNotChecked(@Readonly MethodModeSignature this) {
        return new SigCell();
    }

    @AS
    void abstractStateAllowsMutable(@Mutable MethodModeSignature this, @Mutable SigCell c) {}

    @CS
    void concreteStateAllowsMutable(@Mutable MethodModeSignature this, @Mutable SigCell c) {}

    void unannotatedAllowsMutable(@Mutable MethodModeSignature this, @Mutable SigCell c) {}
}
