// @skip-test : superclass type arguments are not viewpoint-adapted through the receiver. The
// Mutability factory overrides postDirectSuperTypes without the framework's adaptation, so a
// @ReceiverDependentMutable argument does not follow the receiver in any mode, and readonly-state
// and transitive-state do not hide a written @Mutable argument. Remove this line once fixed.

import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.ConcreteState;
import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.MutabilityLost;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;
import org.checkerframework.checker.mutability.qual.TransitiveState;

// A superclass's written type arguments are adapted through the receiver in the current method
// mode (GAIT paper, Example 2). A written @Mutable argument stays @Mutable in abstract-state and
// concrete-state. In readonly-state and transitive-state it is hidden behind @MutabilityLost
// through
// a non-@Mutable receiver. A @ReceiverDependentMutable argument follows the receiver in every mode.
@ReceiverDependentMutable class ViewItem {}

@ReceiverDependentMutable class ViewAbstractBox<T extends @Readonly Object> {}

@ReceiverDependentMutable class ViewMutableArgBox extends ViewAbstractBox<@Mutable ViewItem> {}

@ReceiverDependentMutable class ViewRdmArgBox extends ViewAbstractBox<@ReceiverDependentMutable ViewItem> {}

@SuppressWarnings({"mutability.lost.lhs", "mutability.lost.type.argument"})
class MethodModeInheritedViews {
    @AbstractState
    void abstractStateKeepsMutableArgument(@Readonly ViewMutableArgBox b) {
        @Readonly ViewAbstractBox<@Mutable ViewItem> v = b;
    }

    @ConcreteState
    void concreteStateKeepsMutableArgument(@Readonly ViewMutableArgBox b) {
        @Readonly ViewAbstractBox<@Mutable ViewItem> v = b;
    }

    @ReadonlyState
    void readonlyStateHidesMutableArgument(
            @Readonly MethodModeInheritedViews this, @Readonly ViewMutableArgBox b) {
        @Readonly ViewAbstractBox<@MutabilityLost ViewItem> lost = b;
        // :: error: (assignment.type.incompatible)
        @Readonly ViewAbstractBox<@Mutable ViewItem> v = b;
    }

    @TransitiveState
    void transitiveStateHidesMutableArgument(
            @Readonly MethodModeInheritedViews this, @Readonly ViewMutableArgBox b) {
        @Readonly ViewAbstractBox<@MutabilityLost ViewItem> lost = b;
        // :: error: (assignment.type.incompatible)
        @Readonly ViewAbstractBox<@Mutable ViewItem> v = b;
    }

    @ReadonlyState
    void readonlyStateMutableReceiverKeepsMutableArgument(@Readonly MethodModeInheritedViews this) {
        @Mutable ViewMutableArgBox m = new @Mutable ViewMutableArgBox();
        @Mutable ViewAbstractBox<@Mutable ViewItem> v = m;
    }

    @AbstractState
    void receiverDependentArgumentFollowsImmutableReceiver(@Immutable ViewRdmArgBox b) {
        @Immutable ViewAbstractBox<@Immutable ViewItem> v = b;
        // :: error: (assignment.type.incompatible)
        @Immutable ViewAbstractBox<@Mutable ViewItem> w = b;
    }

    @ReadonlyState
    void receiverDependentArgumentFollowsReceiverInReadonlyState(
            @Readonly MethodModeInheritedViews this, @Immutable ViewRdmArgBox b) {
        @Immutable ViewAbstractBox<@Immutable ViewItem> v = b;
    }

    @AbstractState
    void receiverDependentArgumentThroughReadonlyIsLost(@Readonly ViewRdmArgBox b) {
        @Readonly ViewAbstractBox<@MutabilityLost ViewItem> v = b;
    }
}
