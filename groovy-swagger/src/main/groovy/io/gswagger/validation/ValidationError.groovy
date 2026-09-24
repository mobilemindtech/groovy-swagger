package io.gswagger.validation

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor

/**
 * One violation. `location` points at the value, e.g. `body.address.zipCode`, `body[2].id`,
 * `query.limit`, `path.id`, `header.X-Tenant`, `status`, `content-type`.
 */
@CompileStatic
@TupleConstructor
@EqualsAndHashCode
class ValidationError {
    String location
    String message

    Map<String, String> toMap() { [location: location, message: message] }

    @Override
    String toString() { "$location: $message" }
}
