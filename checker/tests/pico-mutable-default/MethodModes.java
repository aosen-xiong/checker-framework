import org.checkerframework.checker.mutability.qual.AS;
import org.checkerframework.checker.mutability.qual.CS;
import org.checkerframework.checker.mutability.qual.RS;
import org.checkerframework.checker.mutability.qual.TS;

// Each method declares at most one mode. A method without one is checked in AS.
class MethodModes {
    void unannotated() {}

    @AS
    void abstractState() {}

    @CS
    void concreteState() {}

    @RS
    void readonlyState() {}

    @TS
    void transitiveState() {}

    @AS
    @RS
    // :: error: (method.mode.multiple)
    void twoModes() {}
}
