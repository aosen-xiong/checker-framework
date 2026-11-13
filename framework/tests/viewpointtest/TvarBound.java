import viewpointtest.quals.*;

// Regression test for a viewpoint-adaptation defect.
// A class type parameter's receiver-dependent bound is viewpoint-adapted through the receiver of a
// member access, like a field declared with the same qualifier.
public class TvarBound {
    static class Box<X extends @ReceiverDependentQual Object> {
        @ReceiverDependentQual Object f;
        X item;

        // The receiver is Box's own type, so its type argument X is within X's bound.
        void topReceiver(@Top Box<X> this) {
            // :: error: (assignment.type.incompatible)
            @ReceiverDependentQual Object fromField = this.f;
            // Through a @Top receiver, the bound of X is @Lost Object.
            // :: error: (assignment.type.incompatible)
            @ReceiverDependentQual Object fromItem = this.item;
            // :: error: (assignment.type.incompatible)
            @ReceiverDependentQual Object implicitItem = item;
            @Top Object top = this.item;
        }

        // Through a @ReceiverDependentQual receiver, adaptation leaves both types unchanged.
        void receiverDependentReceiver(@ReceiverDependentQual Box<X> this) {
            @ReceiverDependentQual Object fromField = this.f;
            @ReceiverDependentQual Object fromItem = this.item;
        }

        void aReceiver(@A Box<X> this) {
            @A Object fromItem = this.item;
            // :: error: (assignment.type.incompatible)
            @B Object notB = this.item;
        }
    }

    static class FBounded<X extends @ReceiverDependentQual Comparable<X>> {
        X item;

        void topReceiver(@Top FBounded<X> this) {
            // :: error: (assignment.type.incompatible)
            @ReceiverDependentQual Comparable<X> fromItem = this.item;
        }
    }
}
