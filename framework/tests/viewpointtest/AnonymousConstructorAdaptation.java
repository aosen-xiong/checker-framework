import viewpointtest.quals.*;

// The constructor of an anonymous class takes its parameter types and its return type annotations
// from the super constructor, which is viewpoint-adapted to the created type.
public class AnonymousConstructorAdaptation {

    static class Dependent {
        @SuppressWarnings({"inconsistent.constructor.type", "super.invocation.invalid"})
        @ReceiverDependentQual Dependent(@ReceiverDependentQual Object o) {}
    }

    void dependent(@A Object aObj, @B Object bObj) {
        @A Dependent a = new @A Dependent(aObj) {};
        @B Dependent b = new @B Dependent(bObj) {};
        // :: error: (argument.type.incompatible)
        new @A Dependent(bObj) {};
        // :: error: (assignment.type.incompatible)
        @B Dependent bad = new @A Dependent(aObj) {};
    }

    // The anonymous constructor of an inner class has the enclosing instance as an additional
    // first parameter.
    class Inner {
        @SuppressWarnings({"inconsistent.constructor.type", "super.invocation.invalid"})
        @ReceiverDependentQual Inner(@ReceiverDependentQual Object o) {}
    }

    void inner(@A Object aObj, @B Object bObj) {
        @A Inner a = new @A Inner(aObj) {};
        // :: error: (argument.type.incompatible)
        new @A Inner(bObj) {};
        // :: error: (assignment.type.incompatible)
        @B Inner bad = this.new @A Inner(aObj) {};
    }
}
