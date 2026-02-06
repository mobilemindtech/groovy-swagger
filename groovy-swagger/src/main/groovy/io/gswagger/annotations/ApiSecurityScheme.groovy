package io.gswagger.annotations

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface ApiSecurityScheme {
    String name() // Nome de referência (ex: "bearerAuth")
    SecurityType type()
    String headerName() default "" // Para Custom Header
    String description() default ""
}