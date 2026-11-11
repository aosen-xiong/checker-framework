import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// https://github.com/eisop/checker-framework/issues/2296
// A use @q X views the bound of X with main qualifier q and type arguments widened to
// @MutabilityLost (GAIT rule ST-Qual-Class), because a receiver-dependent superclass argument of
// the type that X stands for follows q.
@ReceiverDependentMutable class Issue2296Data {
    int value;

    Issue2296Data(int value) {
        this.value = value;
    }
}

@ReceiverDependentMutable class Issue2296List<E extends @Readonly Issue2296Data> {
    E elem;

    Issue2296List(E elem) {
        this.elem = elem;
    }
}

// A @Mutable Issue2296Own is a @Mutable Issue2296List<@Mutable Issue2296Data>; an @Immutable one
// is an @Immutable Issue2296List<@Immutable Issue2296Data>.
@ReceiverDependentMutable class Issue2296Own extends Issue2296List<@ReceiverDependentMutable Issue2296Data> {
    Issue2296Own(@ReceiverDependentMutable Issue2296Data elem) {
        super(elem);
    }
}

class Issue2296 {
    static <X extends @Mutable Issue2296List<@Mutable Issue2296Data>> @Mutable Issue2296Data get(
            @Immutable X h) {
        // :: error: (return.type.incompatible)
        return h.elem;
    }

    static <X extends @Mutable Issue2296List<@Mutable Issue2296Data>> void show(@Immutable X h) {
        // :: error: (assignment.type.incompatible)
        @Mutable Issue2296Data mutable = h.elem;
        @Readonly Issue2296Data readonly = h.elem;
        // :: error: (assignment.type.incompatible)
        @Immutable Issue2296List<@Mutable Issue2296Data> view = h;
    }

    static <X extends @Mutable Issue2296List<@Mutable Issue2296Data>> void write(@Immutable X h) {
        // :: error: (illegal.field.write)
        h.elem.value = 42;
    }

    // A use without a qualifier of its own keeps the declared type arguments.
    static <X extends @Mutable Issue2296List<@Mutable Issue2296Data>> @Mutable Issue2296Data plain(
            X h) {
        return h.elem;
    }
}
