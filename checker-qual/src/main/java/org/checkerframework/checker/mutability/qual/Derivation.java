package org.checkerframework.checker.mutability.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a method computes a value from the receiver's abstract state only.
 *
 * <p>A derivation method does not write fields, does not read {@link Assignable} fields, and calls
 * only derivation methods, side-effect-free methods, and constructors. Its result is therefore the
 * same whenever it is called on the same object, and may be stored in a {@link Derived} field.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Derivation {}
