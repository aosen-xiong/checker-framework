package org.checkerframework.checker.mutability.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Abstract-state mode: the method body is checked under the ordinary viewpoint adaptation and
 * assignability rules.
 *
 * <p>The mode guarantees that the method preserves the abstract state of each {@code @Immutable}
 * object that exists when it starts. {@link Assignable} fields are not part of that state.
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
public @interface AbstractState {}
