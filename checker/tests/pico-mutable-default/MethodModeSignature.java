import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.ConcreteState;
import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.PolyMutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;
import org.checkerframework.checker.mutability.qual.TransitiveState;

// An readonly-state or transitive-state method's receiver and parameters may not mention @Mutable
// anywhere, including in
// nested type arguments and in the declared bounds of the type variables they use. A bare type
// variable is read through its whole bound. A qualified use such as @Readonly T replaces the head
// of the bound, so only the bound's type arguments are examined. @PolyMutable is checked as poly,
// not as @Mutable. The return type is not checked. abstract-state and concrete-state impose
// nothing.
@Mutable class SigCell {}

@ReceiverDependentMutable class SigBox<T extends @Readonly Object> {}

@ReceiverDependentMutable class SigSelf<T extends @Readonly SigSelf<T>> {}

@ReceiverDependentMutable class MethodModeSignature {
    @ReadonlyState
    void readonlyParameter(@Readonly MethodModeSignature this, @Readonly SigCell c) {}

    @ReadonlyState
    void immutableParameter(@Readonly MethodModeSignature this, @Immutable Object i) {}

    @ReadonlyState
    void receiverDependentReceiver(@ReceiverDependentMutable MethodModeSignature this) {}

    @ReadonlyState
    void mutableReceiver(
            // :: error: (method.mode.signature.mutable)
            @Mutable MethodModeSignature this) {}

    @ReadonlyState
    void mutableParameter(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            @Mutable SigCell c) {}

    @TransitiveState
    void mutableParameterTS(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            @Mutable SigCell c) {}

    @ReadonlyState
    void polyMutable(@PolyMutable MethodModeSignature this, @PolyMutable SigCell c) {}

    @ReadonlyState
    void nestedMutableArgument(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            @Readonly SigBox<@Mutable SigCell> b) {}

    @ReadonlyState
    void nestedReadonlyArgument(
            @Readonly MethodModeSignature this, @Readonly SigBox<@Readonly SigCell> b) {}

    @ReadonlyState
    void mutableArrayComponent(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            @Mutable SigCell @Readonly [] cells) {}

    @ReadonlyState
    void readonlyArray(@Readonly MethodModeSignature this, @Readonly SigCell @Readonly [] cells) {}

    @ReadonlyState
    <T> void bareVariableImplicitBound(@Readonly MethodModeSignature this, T t) {}

    @ReadonlyState
    <T extends @Readonly Object> void bareVariableReadonlyBound(
            @Readonly MethodModeSignature this, T t) {}

    @ReadonlyState
    <T extends @Mutable SigCell> void bareVariableMutableBound(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            T t) {}

    @ReadonlyState
    <T extends @Readonly SigBox<@Mutable SigCell>> void qualifiedVariableBoundArgument(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            @Readonly T t) {}

    @ReadonlyState
    <T extends @Mutable SigBox<@Readonly SigCell>> void qualifiedVariableHidesBoundHead(
            @Readonly MethodModeSignature this, @Readonly T t) {}

    @ReadonlyState
    <T extends @Readonly Object> void qualifiedMutableVariable(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            @Mutable T t) {}

    @ReadonlyState
    <T extends @Readonly Object, U extends T> void bareVariableBoundedByVariable(
            @Readonly MethodModeSignature this,
            // :: error: (method.mode.signature.mutable)
            U u) {}

    @ReadonlyState
    <T extends @Readonly SigSelf<T>> void fBoundedVariableTerminates(
            @Readonly MethodModeSignature this, T t) {}

    @ReadonlyState
    static void staticMethodHasNoReceiver(@Readonly SigCell c) {}

    @ReadonlyState
    @Mutable SigCell returnTypeIsNotChecked(@Readonly MethodModeSignature this) {
        return new SigCell();
    }

    @AbstractState
    void abstractStateAllowsMutable(@Mutable MethodModeSignature this, @Mutable SigCell c) {}

    @ConcreteState
    void concreteStateAllowsMutable(@Mutable MethodModeSignature this, @Mutable SigCell c) {}

    void unannotatedAllowsMutable(@Mutable MethodModeSignature this, @Mutable SigCell c) {}
}
