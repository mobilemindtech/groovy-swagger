package io.gswagger.annotations

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiOperation {
    String path() default  "/"
    ApiMethod method() default ApiMethod.GET
    String description() default ""
    String summary() default ""
    boolean deprecated() default  false
}