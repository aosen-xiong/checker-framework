import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.Assignable;
import org.checkerframework.checker.mutability.qual.ConcreteState;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;
import org.checkerframework.checker.mutability.qual.TransitiveState;

// Concrete-state and transitive-state change field assignability: every instance field, including
// an @Assignable one, is writable only through a @Mutable receiver. Abstract-state and
// readonly-state keep the ordinary rule, under which an @Assignable field is writable through any
// receiver and any other instance field only through a @Mutable one. Static fields and array
// elements are not affected.
@Mutable class AsgCell {
    @Assignable int assignable;
    int plain;
    static int counter;
}

@ReceiverDependentMutable class AsgHolder {
    @Assignable int count;

    @AbstractState
    void bumpAbstractState(@Readonly AsgHolder this) {
        count = 1;
    }

    @ConcreteState
    void bumpConcreteState(@Readonly AsgHolder this) {
        // :: error: (illegal.field.write)
        count = 1;
    }

    @TransitiveState
    void bumpTransitiveState(@Readonly AsgHolder this) {
        // :: error: (illegal.field.write)
        this.count = 1;
    }

    @ConcreteState
    void bumpThroughMutable(@Mutable AsgHolder this) {
        count = 1;
    }
}

class MethodModeAssignability {
    @AbstractState
    void abstractStateAssignable(@Readonly AsgCell c) {
        c.assignable = 1;
    }

    @ReadonlyState
    void readonlyStateAssignable(@Readonly MethodModeAssignability this, @Readonly AsgCell c) {
        c.assignable = 1;
    }

    @ConcreteState
    void concreteStateAssignable(@Readonly AsgCell c) {
        // :: error: (illegal.field.write)
        c.assignable = 1;
    }

    @TransitiveState
    void transitiveStateAssignable(@Readonly MethodModeAssignability this, @Readonly AsgCell c) {
        // :: error: (illegal.field.write)
        c.assignable = 1;
    }

    @ConcreteState
    void concreteStateCompoundAssignment(@Readonly AsgCell c) {
        // :: error: (illegal.field.write)
        c.assignable += 1;
    }

    @ConcreteState
    void concreteStateIncrement(@Readonly AsgCell c) {
        // :: error: (illegal.field.write)
        c.assignable++;
    }

    @ConcreteState
    void concreteStateThroughMutable(@Mutable AsgCell c) {
        c.assignable = 1;
        c.plain = 1;
    }

    @ConcreteState
    void concreteStatePlainField(@Readonly AsgCell c) {
        // :: error: (illegal.field.write)
        c.plain = 1;
    }

    @AbstractState
    void abstractStatePlainField(@Readonly AsgCell c) {
        // :: error: (illegal.field.write)
        c.plain = 1;
    }

    @ConcreteState
    void concreteStateStaticField() {
        AsgCell.counter = 1;
    }

    @ConcreteState
    void concreteStateLambda(@Readonly AsgCell c) {
        Runnable r =
                () -> {
                    // :: error: (illegal.field.write)
                    c.assignable = 1;
                };
    }

    @AbstractState
    void abstractStateLambda(@Readonly AsgCell c) {
        Runnable r =
                () -> {
                    c.assignable = 1;
                };
    }
}
