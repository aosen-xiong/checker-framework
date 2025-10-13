package org.checkerframework.checker.nullness.qual;

import org.checkerframework.framework.qual.ReadWriteDynamicQualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A read-write sensitive qualifier for the non-null type system. Programmer should never write this
 * annotation directly, and it is intended to applied to unannotated fields only by the type system.
 *
 * <p>Any field written using {@link ReadWriteDynamicNull} behaves differently depending on whether
 * conservative or optimistic default is applied. Under conservative default, the use of {@link
 * ReadWriteDynamicNull} is equivalent to {@link Nullable} in field read, and the use of {@link
 * ReadWriteDynamicNull} is equivalent to {@link NonNull} in field write. It is a sound option for
 * unannotated code. Under optimistic default, the use of {@link ReadWriteDynamicNull} is equivalent
 * to {@link NonNull} in field read, and the use of {@link ReadWriteDynamicNull} is equivalent to
 * {@link Nullable} in field write. This option is used for reduce write errors in legacy code and
 * is not sound.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@ReadWriteDynamicQualifier(NonNull.class)
public @interface ReadWriteDynamicNull {}
