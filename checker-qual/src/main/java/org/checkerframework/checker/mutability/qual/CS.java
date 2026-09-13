package org.checkerframework.checker.mutability.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Concrete-state mode: an {@link Assignable} field is writable only through a {@code @Mutable} receiver.
 *
 * <p>Compared with {@link AS}, {@link Assignable} acts as receiver-dependent assignable inside the method body, so the method preserves every field of each {@code @Immutable} object that exists when it starts.
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
public @interface CS {}
