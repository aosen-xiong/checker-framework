package org.checkerframework.checker.mutability.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Readonly-state mode: a {@code @Mutable} member read through a non-{@code @Mutable} receiver
 * adapts to {@code @MutabilityLost}.
 *
 * <p>Compared with {@link AS}, a call that starts with no mutable reference cannot obtain one, so
 * the method preserves every non-{@link Assignable} field reachable from its receiver and
 * arguments. The guarantee also requires a signature, bounds included, that mentions no
 * {@code @Mutable}.
 *
 * <p>A method has at most one mode annotation. A method without one is checked in {@link AS}. A
 * method may call methods of its own mode or a stronger one: {@link TS} is the strongest, {@link
 * RS} and {@link CS} both refine {@link AS}, and {@link RS} and {@link CS} are incomparable.
 *
 * @see AS
 * @see CS
 * @see RS
 * @see TS
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RS {}
