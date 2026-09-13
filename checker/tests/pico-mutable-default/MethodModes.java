import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.ConcreteState;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.TransitiveState;

// Each method declares at most one mode. A method without one is checked in abstract-state. An
// readonly-state or transitive-state method
// may not have a @Mutable receiver, so those methods declare a @Readonly one.
class MethodModes {
    void unannotated() {}

    @AbstractState
    void abstractState() {}

    @ConcreteState
    void concreteState() {}

    @ReadonlyState
    void readonlyState(@Readonly MethodModes this) {}

    @TransitiveState
    void transitiveState(@Readonly MethodModes this) {}

    @AbstractState
    @ReadonlyState
    // :: error: (method.mode.multiple)
    void twoModes() {}
}
