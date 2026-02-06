package io.gswagger.annotations

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface ApiSchema {
    String name() default ""
    Class type() default Void
    boolean isList() default false
    String description() default ""
    String since() default ""
    String example() default ""
    ApiSchemaField[] fields() default []
}