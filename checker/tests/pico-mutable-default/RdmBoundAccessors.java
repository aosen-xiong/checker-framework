import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

// Accessors of a class whose type parameter has a @ReceiverDependentMutable bound.
//
// Two bare uses of the same type parameter are the same type, whatever receiver each was viewed
// through: through a @Readonly receiver the bound of E is lost (eisop#2516), but the value is
// still an E.
//
// The GAIT paper also accepts the getter and the setter below. The checker rejects them because
// it defaults a field written "E element" to "@ReceiverDependentMutable E", which reads as
// "@MutabilityLost E" through a @Readonly receiver and as "@Mutable E" through a @Mutable one.
// Defaulting it to the bare E instead would expose a separate bug: the target of a write to a
// type-variable field is typed by the erased bound, not by the type argument.
@ReceiverDependentMutable class AccItem {
    int n;
}

@ReceiverDependentMutable class AccBox<E extends @ReceiverDependentMutable Object> {
    E element;

    AccBox(E element) {
        this.element = element;
    }

    E get(@Readonly AccBox<E> this) {
        // :: error: (return.type.incompatible)
        return element;
    }

    void set(@Mutable AccBox<E> this, E e) {
        // :: error: (assignment.type.incompatible)
        element = e;
    }

    // The call returns E viewed through the @Readonly receiver, whose bound is lost.
    E viaGetter(@Readonly AccBox<E> this) {
        return get();
    }

    E viaGetterLocal(@Readonly AccBox<E> this) {
        E local = get();
        return local;
    }
}

class RdmBoundAccessors {
    void clients(@Mutable AccBox<@Mutable AccItem> m, @Immutable AccBox<@Immutable AccItem> i) {
        @Mutable AccItem fromMutable = m.get();
        fromMutable.n = 1;
        @Immutable AccItem fromImmutable = i.get();
        // :: error: (assignment.type.incompatible)
        @Mutable AccItem wrong = i.get();
        m.set(new @Mutable AccItem());
        // :: error: (method.invocation.invalid)
        i.set(new @Immutable AccItem());
    }
}
