package io.gswagger.annotations

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention(RetentionPolicy.RUNTIME)
@interface ApiServer {
    String url()
    String description() default  ""
}