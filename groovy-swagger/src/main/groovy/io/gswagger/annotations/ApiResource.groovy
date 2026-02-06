package io.gswagger.annotations

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface ApiResource {
    String path()
    String description() default ""
    String since() default ""
    String version() default ""
    ApiContent[] contents() default []
}