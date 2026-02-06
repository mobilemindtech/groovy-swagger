package io.gswagger.annotations

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.TYPE, ElementType.METHOD])
@interface ApiHeader {
    String name()
    String description() default ""
    boolean required() default false
    String example() default ""
    ApiParam[] schema() default []
    Class type() default Void
    String since() default  ""
}