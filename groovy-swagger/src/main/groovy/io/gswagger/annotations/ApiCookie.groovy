package io.gswagger.annotations

import java.lang.annotation.ElementType
import java.lang.annotation.Target

@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiCookie {
    String name()
    ApiParam[] schema() default []
    Class type() default Void
    String description() default ""
    String example() default ""
    String since() default  ""
    boolean required() default false
    boolean allowEmptyValue() default true
}