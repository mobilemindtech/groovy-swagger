package io.gswagger.validation

import groovy.transform.CompileStatic

/**
 * Validation behaviour that the OpenAPI document itself doesn't express.
 */
@CompileStatic
class ValidationOptions {

    /**
     * Accept object properties that aren't declared in the schema. Only used when the schema
     * doesn't set `additionalProperties` itself (an explicit value in the schema always wins).
     */
    boolean allowAdditionalProperties = true

    /**
     * Accept `null` for properties that aren't required. Required properties are never allowed to
     * be null. The generator doesn't emit `nullable`, and Grails renders null fields, so the default is true.
     */
    boolean allowNullValues = true

    /** Fail responses whose status code isn't documented for the operation (and there is no `default`). */
    boolean failOnUndocumentedStatus = true

    /**
     * Path prefix removed before matching request paths against the document paths. When null the
     * path component of every `servers[].url` is tried (e.g. `/api` for `http://host/api`).
     */
    String basePath
}
