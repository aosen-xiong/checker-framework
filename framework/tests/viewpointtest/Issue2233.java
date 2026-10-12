// Test case for EISOP issue #2233:
// https://github.com/eisop/checker-framework/issues/2233

import viewpointtest.quals.*;

public class Issue2233 {
    static class Tool {
        void take(@A Tool this, @PolyVP Object o) {}

        @PolyVP Object id(@A Tool this, @PolyVP Object o) {
            return o;
        }
    }

    static class Source {
        @PolyVP Object make(@A Source this) {
            return null;
        }

        @PolyVP Object makeFrom(@A Source this, @PolyVP Object... os) {
            return null;
        }
    }

    void noInstantiation(@A Source s) {
        // Nothing instantiates @PolyVP, so it is @Bottom.
        @B Object b = s.make();
        // A varargs call without arguments instantiates @PolyVP to @Top.
        @Top Object top = s.makeFrom();
        // :: error: (assignment.type.incompatible)
        @B Object bad = s.makeFrom();
    }

    void pass(@A Tool t, @ReceiverDependentQual Object x) {
        // @PolyVP is instantiated by the argument; the receiver's viewpoint does not rewrite it.
        t.take(x);
        @ReceiverDependentQual Object r = t.id(x);
        // :: error: (assignment.type.incompatible)
        @A Object a = t.id(x);
    }

    static class Made {
        Made(@PolyVP Object o) {}
    }

    void construct(@ReceiverDependentQual Object x) {
        // :: warning: (cast.unsafe.constructor.invocation)
        new @A Made(x);
        // :: warning: (cast.unsafe.constructor.invocation)
        new @A Made(x) {};
    }

    static class GenericMade<T> {
        GenericMade(@PolyVP T t) {}
    }

    void constructGeneric(@ReceiverDependentQual Object x) {
        // :: warning: (cast.unsafe.constructor.invocation)
        new @A GenericMade<Object>(x);
    }

    static class Box<E> {
        E get(@PolyVP Object o) {
            return null;
        }

        void put(@PolyVP E e) {}
    }

    // The @PolyVP in the type argument of the receiver is the one of this method, not the one of
    // Box.get, so the call does not instantiate it.
    @PolyVP Object receiverTypeArgument(Box<@PolyVP Object> b, @A Object a) {
        // :: error: (assignment.type.incompatible)
        @A Object bad = b.get(a);
        // :: error: (argument.type.incompatible)
        b.put(a);
        return b.get(a);
    }
}
