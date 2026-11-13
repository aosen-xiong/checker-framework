import viewpointtest.quals.*;

// A use @q X whose bound has a type variable in its type arguments has no view through the bound:
// a later substitution can supply a receiver-dependent qualifier that q has just replaced. No
// field or method is reachable through it, and it is not a subtype of a generic class type.
public class RequalifiedNonGroundBound {
    static class Node<T extends @Top Object> {
        T next;
    }

    static <T extends @B Node<T>> void walk(@A T n) {
        // :: error: (type.variable.use.member)
        @Top Object next = n.next;
        // :: error: (assignment.type.incompatible)
        @A Node<T> view = n;
        @A Object o = n;
    }

    // A bare use stands for the whole type argument, so its members are reachable.
    static <T extends @B Node<T>> @B Object plain(T n) {
        return n.next;
    }
}
