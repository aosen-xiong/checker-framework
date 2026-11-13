package org.checkerframework.checker.mutability.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Concrete-state mode: an {@link Assignable} field is writable only through a {@code @Mutable}
 * receiver.
 *
 * <p>Compared with {@link AbstractState}, {@link Assignable} acts as receiver-dependent assignable
 * inside the method body, so the method preserves every field of each {@code @Immutable} object
 * that exists when it starts.
 *
 * <p>A method has at most one mode annotation. A method without one is checked in {@link
 * AbstractState}. A method may call methods of its own mode or a stronger one: {@link
 * TransitiveState} is the strongest, {@link ReadonlyState} and {@link ConcreteState} both refine
 * {@link AbstractState}, and {@link ReadonlyState} and {@link ConcreteState} are incomparable.
 *
 * @see AbstractState
 * @see ConcreteState
 * @see ReadonlyState
 * @see TransitiveState
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface ConcreteState {}
