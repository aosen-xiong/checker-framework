package org.checkerframework.framework.qual;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A meta-annotation that indicates that an annotation is a read-write sensitive type qualifier.
 *
 * <p>Any field written using a read-write sensitive type qualifier behaves differently depending on
 * whether conservative or optimistic default is applied. Under conservative default, the use of a
 * read-write sensitive type qualifier is equivalent to the top qualifier in field read, and the use
 * of a read-write sensitive type qualifier is equivalent to the bottom qualifier in field write. It
 * is a sound option for unannotated code. Under optimistic default, the use of a read-write
 * sensitive type qualifier is equivalent to the bottom qualifier in field read, and the use of a
 * read-write sensitive type qualifier is equivalent to the top qualifier in field write. This
 * option is used for reduce write errors in legacy code and is not sound.
 *
 * @checker_framework.manual #qualifier-read-write-sensitivity Read-write sensitivity
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE})
public @interface ReadWriteDynamicQualifier {
    Class<? extends Annotation> value();
}
