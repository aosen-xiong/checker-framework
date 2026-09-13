package org.checkerframework.checker.mutability;

import org.checkerframework.checker.mutability.qual.AbstractState;
import org.checkerframework.checker.mutability.qual.ConcreteState;
import org.checkerframework.checker.mutability.qual.ReadonlyState;
import org.checkerframework.checker.mutability.qual.TransitiveState;

import java.lang.annotation.Annotation;

/**
 * The mode a method body is checked in. A method declares its mode with one of {@link
 * AbstractState}, {@link ConcreteState}, {@link ReadonlyState}, or {@link TransitiveState}, and a
 * method without one is checked in {@link #ABSTRACT_STATE}.
 */
public enum MethodMode {
    /** Abstract state: the ordinary viewpoint adaptation and assignability rules. */
    ABSTRACT_STATE(AbstractState.class),
    /** Concrete state: {@code @Assignable} fields are writable only through {@code @Mutable}. */
    CONCRETE_STATE(ConcreteState.class),
    /**
     * Readonly state: a {@code @Mutable} member read through a non-{@code @Mutable} receiver is
     * lost.
     */
    READONLY_STATE(ReadonlyState.class),
    /** Transitive state: the {@link #CONCRETE_STATE} and {@link #READONLY_STATE} rules together. */
    TRANSITIVE_STATE(TransitiveState.class);

    /** The declaration annotation that selects this mode. */
    public final Class<? extends Annotation> annotation;

    /**
     * Creates a mode selected by {@code annotation}.
     *
     * @param annotation the declaration annotation that selects this mode
     */
    MethodMode(Class<? extends Annotation> annotation) {
        this.annotation = annotation;
    }

    /**
     * Returns true if this mode changes value viewpoint adaptation. {@link #ABSTRACT_STATE} and
     * {@link #CONCRETE_STATE} share the ordinary value adaptation, {@link #READONLY_STATE} and
     * {@link #TRANSITIVE_STATE} do not.
     *
     * @return true for {@link #READONLY_STATE} and {@link #TRANSITIVE_STATE}
     */
    public boolean changesValueAdaptation() {
        return this == READONLY_STATE || this == TRANSITIVE_STATE;
    }

    /**
     * Returns true if this mode restricts field assignability. In {@link #CONCRETE_STATE} and
     * {@link #TRANSITIVE_STATE}, an instance field, including an {@code @Assignable} one, is
     * writable only through a {@code @Mutable} receiver.
     *
     * @return true for {@link #CONCRETE_STATE} and {@link #TRANSITIVE_STATE}
     */
    public boolean restrictsAssignability() {
        return this == CONCRETE_STATE || this == TRANSITIVE_STATE;
    }

    /**
     * Returns true if a method in this mode may call a method in {@code callee}'s mode. The
     * callee's mode must be at least as strong as the caller's. {@link #TRANSITIVE_STATE} is the
     * strongest, and {@link #READONLY_STATE} and {@link #CONCRETE_STATE} each refine {@link
     * #ABSTRACT_STATE} but are incomparable.
     *
     * @param callee the mode of the called method
     * @return true if a method in this mode may call a method in {@code callee}'s mode
     */
    public boolean allowsCall(MethodMode callee) {
        return callee == this || callee == TRANSITIVE_STATE || this == ABSTRACT_STATE;
    }

    /**
     * Returns the annotation that selects this mode, as written in source, for diagnostics.
     *
     * @return the annotation that selects this mode, such as {@code @ReadonlyState}
     */
    @Override
    public String toString() {
        return "@" + annotation.getSimpleName();
    }
}
