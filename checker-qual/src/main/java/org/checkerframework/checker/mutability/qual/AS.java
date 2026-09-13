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
public @interface AS {}
