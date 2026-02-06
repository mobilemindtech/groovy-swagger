package io.gswagger.annotations

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiPathParam {
    String name()
    ApiParam[] schema() default []
    Class type() default Void
    String description() default ""
    String since() default  ""
    String example() default ""
    boolean required() default true
    boolean allowEmptyValue() default false
}