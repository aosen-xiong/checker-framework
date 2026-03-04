package org.checkerframework.checker.pico.qual;

import org.checkerframework.checker.initialization.qual.HoldsForDefaultValue;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to indicate the field is final but its initialization can be deferred
 * (i.e., lazy initialization). It should only annotate on field, not class or method.
 *
 * <p>It is different from {@code Assignable} in that {@code LazyFinal} fields can only be assigned
 * once, and the field has this annotation is considered as part of the abstract state.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@HoldsForDefaultValue
public @interface LazyFinal {}
