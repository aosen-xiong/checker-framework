import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// Regression test for a viewpoint-adaptation defect.
// A use @q X views the bound of X with main qualifier q and type arguments widened to
// @MutabilityLost (GAIT rule ST-Qual-Class), because a receiver-dependent superclass argument of
// the type that X stands for follows q.
@ReceiverDependentMutable class WidenViewData {
    int value;

    WidenViewData(int value) {
        this.value = value;
    }
}

@ReceiverDependentMutable class WidenViewList<E extends @Readonly WidenViewData> {
    E elem;

    WidenViewList(E elem) {
        this.elem = elem;
    }
}

// A @Mutable WidenViewOwn is a @Mutable WidenViewList<@Mutable WidenViewData>; an @Immutable one
// is an @Immutable WidenViewList<@Immutable WidenViewData>.
@ReceiverDependentMutable class WidenViewOwn extends WidenViewList<@ReceiverDependentMutable WidenViewData> {
    WidenViewOwn(@ReceiverDependentMutable WidenViewData elem) {
        super(elem);
    }
}

class WidenView {
    static <X extends @Mutable WidenViewList<@Mutable WidenViewData>> @Mutable WidenViewData get(
            @Immutable X h) {
        // :: error: (return.type.incompatible)
        return h.elem;
    }

    static <X extends @Mutable WidenViewList<@Mutable WidenViewData>> void show(@Immutable X h) {
        // :: error: (assignment.type.incompatible)
        @Mutable WidenViewData mutable = h.elem;
        @Readonly WidenViewData readonly = h.elem;
        // :: error: (assignment.type.incompatible)
        @Immutable WidenViewList<@Mutable WidenViewData> view = h;
    }

    static <X extends @Mutable WidenViewList<@Mutable WidenViewData>> void write(@Immutable X h) {
        // :: error: (illegal.field.write)
        h.elem.value = 42;
    }

    // A use without a qualifier of its own keeps the declared type arguments.
    static <X extends @Mutable WidenViewList<@Mutable WidenViewData>> @Mutable WidenViewData plain(
            X h) {
        return h.elem;
    }
}
