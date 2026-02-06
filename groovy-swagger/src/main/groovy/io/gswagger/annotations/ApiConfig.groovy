package io.gswagger.annotations

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface ApiConfig {
    String openapi() default "3.1.1"
    String title() default ""
    String version() default ""
    String description() default ""
    ApiSecurityScheme[] securitySchemes() default []
    ApiSchema[] schemas() default []
    ApiServer[] servers() default []
    ApiContent[] contents() default []
}