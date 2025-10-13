package org.checkerframework.checker.nullness.qual;

import org.checkerframework.framework.qual.ReadWriteDynamicQualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@ReadWriteDynamicQualifier(NonNull.class)
public @interface RWNull {}
