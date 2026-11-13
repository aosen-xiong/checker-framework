import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.ConcreteState;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.TransitiveState;

// A method may call a method whose mode is at least as strong as its own. Transitive-state is the
// strongest. Readonly-state and concrete-state each refine abstract-state and are incomparable. A
// method without a mode annotation, including a JDK method, is abstract-state. Object creation is
// allowed in every mode. The callees take @Readonly receivers so that only the mode rule is tested.
class MethodModeCalls {
    void unannotatedCallee(@Readonly MethodModeCalls this) {}

    @AbstractState
    void abstractCallee(@Readonly MethodModeCalls this) {}

    @ConcreteState
    void concreteCallee(@Readonly MethodModeCalls this) {}

    @ReadonlyState
    void readonlyCallee(@Readonly MethodModeCalls this) {}

    @TransitiveState
    void transitiveCallee(@Readonly MethodModeCalls this) {}

    static void staticCallee() {}

    @AbstractState
    void fromAbstract(@Readonly MethodModeCalls this) {
        unannotatedCallee();
        abstractCallee();
        concreteCallee();
        readonlyCallee();
        transitiveCallee();
        staticCallee();
    }

    @ConcreteState
    void fromConcrete(@Readonly MethodModeCalls this) {
        // :: error: (method.mode.call.invalid)
        unannotatedCallee();
        // :: error: (method.mode.call.invalid)
        abstractCallee();
        concreteCallee();
        // :: error: (method.mode.call.invalid)
        readonlyCallee();
        transitiveCallee();
    }

    @ReadonlyState
    void fromReadonly(@Readonly MethodModeCalls this) {
        // :: error: (method.mode.call.invalid)
        unannotatedCallee();
        // :: error: (method.mode.call.invalid)
        abstractCallee();
        // :: error: (method.mode.call.invalid)
        concreteCallee();
        readonlyCallee();
        transitiveCallee();
        // :: error: (method.mode.call.invalid)
        staticCallee();
    }

    @TransitiveState
    void fromTransitive(@Readonly MethodModeCalls this) {
        // :: error: (method.mode.call.invalid)
        unannotatedCallee();
        // :: error: (method.mode.call.invalid)
        abstractCallee();
        // :: error: (method.mode.call.invalid)
        concreteCallee();
        // :: error: (method.mode.call.invalid)
        readonlyCallee();
        transitiveCallee();
    }

    @ReadonlyState
    void jdkMethodIsAbstractState(@Readonly MethodModeCalls this, String s) {
        // :: error: (method.mode.call.invalid)
        s.length();
    }

    @ReadonlyState
    void lambdaUsesEnclosingMode(@Readonly MethodModeCalls this) {
        Runnable r =
                () -> {
                    // :: error: (method.mode.call.invalid)
                    abstractCallee();
                };
    }

    @ReadonlyState
    void objectCreationIsAllowed(@Readonly MethodModeCalls this) {
        new Object();
    }

    void unannotatedCallerMayCallAnything(@Readonly MethodModeCalls this) {
        concreteCallee();
        readonlyCallee();
        transitiveCallee();
    }
}

// An overriding method must have exactly the mode of every method it overrides. A stronger mode is
// rejected as well as a weaker one.
class ModeCallBase {
    @ReadonlyState
    void sameMode(@Readonly ModeCallBase this) {}

    void unannotated(@Readonly ModeCallBase this) {}

    @TransitiveState
    void transitive(@Readonly ModeCallBase this) {}
}

class ModeCallSub extends ModeCallBase {
    @Override
    @ReadonlyState
    void sameMode(@Readonly ModeCallSub this) {}

    @Override
    @ReadonlyState
    // :: error: (method.mode.override.invalid)
    void unannotated(@Readonly ModeCallSub this) {}

    @Override
    @ReadonlyState
    // :: error: (method.mode.override.invalid)
    void transitive(@Readonly ModeCallSub this) {}
}

interface ModeCallInterface {
    @TransitiveState
    void run(@Readonly ModeCallInterface this);
}

class ModeCallImplementation implements ModeCallInterface {
    @Override
    // :: error: (method.mode.override.invalid)
    public void run(@Readonly ModeCallImplementation this) {}
}
