package org.checkerframework.checker.mutability;

import org.checkerframework.checker.mutability.qual.AS;
import org.checkerframework.checker.mutability.qual.CS;
import org.checkerframework.checker.mutability.qual.RS;
import org.checkerframework.checker.mutability.qual.TS;

import java.lang.annotation.Annotation;

/**
 * The mode a method body is checked in. A method declares its mode with one of {@link AS}, {@link
 * CS}, {@link RS}, or {@link TS}, and a method without one is checked in {@link #AS}.
 */
public enum MethodMode {
    /** Abstract state: the ordinary viewpoint adaptation and assignability rules. */
    AS(AS.class),
    /** Concrete state: {@code @Assignable} fields are writable only through {@code @Mutable}. */
    CS(CS.class),
    /** Readonly state: a {@code @Mutable} member read through a non-{@code @Mutable} receiver is lost. */
    RS(RS.class),
    /** Transitive state: the {@link #CS} and {@link #RS} rule changes together. */
    TS(TS.class);

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
     * Returns true if this mode changes value viewpoint adaptation. {@link #AS} and {@link #CS}
     * share the ordinary value adaptation; {@link #RS} and {@link #TS} do not.
     *
     * @return true for {@link #RS} and {@link #TS}
     */
    public boolean changesValueAdaptation() {
        return this == RS || this == TS;
    }
}
