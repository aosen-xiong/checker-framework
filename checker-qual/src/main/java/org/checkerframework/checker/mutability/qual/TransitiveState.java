package org.checkerframework.checker.mutability.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Transitive-state mode: the {@link ReadonlyState} and {@link ConcreteState} rule changes together.
 *
 * <p>The method preserves every field reachable from its receiver and arguments. Like {@link
 * ReadonlyState}, the guarantee requires a signature, bounds included, that mentions no
 * {@code @Mutable}.
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
public @interface TransitiveState {}
