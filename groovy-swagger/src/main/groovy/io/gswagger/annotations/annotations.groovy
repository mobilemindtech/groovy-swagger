package io.gswagger.annotations

import java.lang.annotation.*

enum ApiMethod {
    GET, PUT, POST, DELETE, PATCH, HEAD, OPTIONS
}
enum SecurityType {
    BEARER, BASIC, API_KEY, NONE
}

class ContentType {
    static final String JSON = "application/json"
    static final String TEXT = "text/plain"
    static final String HTML = "text/html"
    static final String XML = "text/xml"
    static final String STREAM = "application/octet-stream"
}

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

@Retention(RetentionPolicy.RUNTIME)
@interface ApiServer {
    String url()
    String description() default  ""
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface ApiServers {
    ApiServer[] value() default []
}
/**
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface ApiSchema {
    String name() default ""
    Class type() default Void
    boolean isList() default false
    String description() default ""
    String since() default ""
    String example() default ""
    ApiSchemaField[] fields() default []
}


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface ApiSchemas {
    ApiSchema[] value()
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface ApiSchemaField {
    String name() default ""
    String description() default ""
    String since() default ""
    String example() default ""
    Class type() default Void
    ApiSchema schema() default @ApiSchema(name = "__none__")
    boolean required() default false
    boolean  isList() default false
    String[] options() default []
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface ApiResource {
    String path()
    String description() default ""
    String since() default ""
    String version() default ""
    ApiContent[] contents() default []
}

@Target(ElementType.TYPE)
@interface ApiParameterSchema {
    String title() default ""
    String description() default ""
    Class type() default Void
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

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiParam {
    String name()
    ApiParameterSchema schema() default @ApiParameterSchema()
    Class type() default Void
    String description() default ""
    String since() default  ""
    String example() default ""
    boolean required() default true
    boolean allowEmptyValue() default false
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiParams {
    ApiParam[] value()
}

@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiCookie {
    String name()
    ApiParameterSchema schema() default @ApiParameterSchema
    Class type() default Void
    String description() default ""
    String example() default ""
    String since() default  ""
    boolean required() default false
    boolean allowEmptyValue() default true
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiCookies {
    ApiCookie[] value()
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiQuery {
    String name()
    ApiParameterSchema schema() default @ApiParameterSchema
    Class type() default Void
    String description() default ""
    boolean required() default false
    String example() default ""
    String since() default  ""
    boolean allowEmptyValue() default true
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiQueries {
    ApiQuery[] value()
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiContent {
    String contentType()
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.TYPE, ElementType.METHOD])
@interface ApiHeader {
    String name()
    String description() default ""
    boolean required() default false
    String example() default ""
    ApiParameterSchema schema() default @ApiParameterSchema
    Class type() default Void
    String since() default  ""
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.TYPE, ElementType.METHOD])
@interface ApiHeaders {
    ApiHeader[] value()
}
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiRequestBody {
    Class body() default Void
    ApiSchema schema() default @ApiSchema(name = "__none__")
    String since() default  ""
    String description() default ""
    boolean isList() default false
    ApiContent[] contents() default []
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiResponse {
    int statusCode()
    Class body() default Void
    ApiSchema schema() default @ApiSchema(name = "__none__")
    String description() default ""
    boolean isList() default false
    ApiContent[] contents() default []
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiResponses {
    ApiResponse[] value() default []
    ApiContent[] contents() default []
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD])
@interface ApiOperation {
    String path() default  "/"
    ApiMethod method() default ApiMethod.GET
    String description() default ""
    String summary() default ""
    boolean deprecated() default  false
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface ApiSecurityScheme {
    String name() // Nome de referência (ex: "bearerAuth")
    SecurityType type()
    String headerName() default "" // Para Custom Header
    String description() default ""
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.FIELD])
@interface ApiSecurity {
    String value() // Nome do esquema (ex: "BearerAuth")
    String[] scopes() default []
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.FIELD])
@interface ApiSecurities {
    ApiSecurity[] value()
}