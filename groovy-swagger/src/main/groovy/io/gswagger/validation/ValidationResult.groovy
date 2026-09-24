package io.gswagger.validation

import groovy.transform.CompileStatic

@CompileStatic
class ValidationResult {

    /** false when no documented operation matches the method + path (nothing was validated) */
    final boolean matched
    /** `METHOD /documented/{path}` of the matched operation */
    final String operation
    final List<ValidationError> errors

    ValidationResult(boolean matched, String operation, List<ValidationError> errors) {
        this.matched = matched
        this.operation = operation
        this.errors = errors.asImmutable()
    }

    static ValidationResult notMatched() { new ValidationResult(false, null, []) }

    boolean isValid() { errors.isEmpty() }

    Map toMap() {
        [operation: operation, errors: errors*.toMap()]
    }

    @Override
    String toString() {
        valid ? "valid${operation ? " ($operation)" : ''}" : "$operation: ${errors.join('; ')}"
    }
}
