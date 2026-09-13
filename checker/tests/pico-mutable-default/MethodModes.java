import org.checkerframework.checker.mutability.qual.AS;
import org.checkerframework.checker.mutability.qual.CS;
import org.checkerframework.checker.mutability.qual.RS;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.TS;

// Each method declares at most one mode. A method without one is checked in AS. An RS or TS method
// may not have a @Mutable receiver, so those methods declare a @Readonly one.
class MethodModes {
    void unannotated() {}

    @AS
    void abstractState() {}

    @CS
    void concreteState() {}

    @RS
    void readonlyState(@Readonly MethodModes this) {}

    @TS
    void transitiveState(@Readonly MethodModes this) {}

    @AS
    @RS
    // :: error: (method.mode.multiple)
    void twoModes() {}
}
