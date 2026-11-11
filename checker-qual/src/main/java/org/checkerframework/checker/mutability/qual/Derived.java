package org.checkerframework.checker.mutability.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a field is derived state: it is lazily initialized, and every value written to it
 * after construction is computed from the object's abstract state by a {@link Derivation} method.
 *
 * <p>A {@code @Derived} field must also be {@link Assignable}, so it is excluded from the abstract
 * state. The Mutability Checker enforces that each method reads the field at most once, that every
 * write stores the result of a derivation method, and that the field is private. Under these rules
 * unsynchronized lazy initialization of the field does not change the results of the class's
 * methods.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Derived {
    /**
     * The name of the derivation method that computes the field's value. If empty, any derivation
     * method of the class may be used.
     *
     * @return the name of the derivation method, or the empty string
     */
    String by() default "";
}
