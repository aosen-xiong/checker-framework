import viewpointtest.quals.*;

// https://github.com/eisop/checker-framework/issues/2296
// A use @q X views the bound of X with main qualifier q and type arguments widened, because a
// receiver-dependent superclass argument of the type that X stands for follows q.
public class Issue2296 {
    static class Data {}

    static class Box<E extends @Top Object> {
        E elem;
    }

    // A @B Own is a @B Box<@B Data>; an @A Own is an @A Box<@A Data>.
    static class Own extends Box<@ReceiverDependentQual Data> {}

    static <X extends @B Box<@B Data>> @B Data get(@A X h) {
        // :: error: (return.type.incompatible)
        return h.elem;
    }

    void client(@A Own a) {
        @B Data wrong = Issue2296.<@B Own>get(a);
        @A Data right = a.elem;
    }

    static <X extends @B Box<@B Data>> void show(@A X h, @A Own a) {
        // :: error: (assignment.type.incompatible)
        @A Data viaTypeVariable = h.elem;
        @Top Data top = h.elem;
        // :: error: (assignment.type.incompatible)
        @B Data direct = a.elem;
    }

    // A use without a qualifier of its own keeps the declared type arguments.
    static <X extends @B Box<@B Data>> @B Data plain(X h) {
        return h.elem;
    }
}
