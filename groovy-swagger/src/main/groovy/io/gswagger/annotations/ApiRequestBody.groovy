package io.gswagger.annotations

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiRequestBody {
    Class body() default Void
    ApiSchema[] schema() default []
    String since() default  ""
    String description() default ""
    boolean isList() default false
    ApiContent[] contents() default []
}