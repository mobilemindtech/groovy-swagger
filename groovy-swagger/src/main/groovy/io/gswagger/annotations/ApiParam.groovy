package io.gswagger.annotations

import java.lang.annotation.ElementType
import java.lang.annotation.Target

@Target(ElementType.TYPE)
@interface ApiParam {
    String title() default ""
    String description() default ""
    Class type()
    Class typeItem() default Void // is is list
    int maximum() default -1
    int minimum() default -1
    int maxLength() default -1
    int minLength() default -1
    String pattern() default ""
    int maxItems() default -1
    int minItems() default -1
    boolean uniqueItems() default false
    boolean required() default false
    String[] options() default [] // enum property
    String format() default  ""
    String defaultValue() default ""
}