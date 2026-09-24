package org.grails.plugins.swagger

import grails.core.GrailsApplication
import groovy.util.logging.Slf4j
import io.gswagger.core.OpenApiService
import io.gswagger.validation.HttpData
import io.gswagger.validation.OpenApiValidator
import io.gswagger.validation.ValidationOptions
import io.gswagger.validation.ValidationResult

/**
 * Validates requests/responses against the OpenAPI document generated from the annotated controllers
 * (grails.plugins.swagger.config / grails.plugins.swagger.package).
 *
 * Doesn't depend on the servlet API: build an {@link HttpData} from anywhere (controller, job, test)
 * and call {@link #validateRequest} / {@link #validateResponse}. {@link OpenApiValidationFilter} uses it
 * for every HTTP request when grails.plugins.swagger.validation.enabled = true.
 *
 * The document is generated once and cached; {@link #reload()} rebuilds it (called when a controller
 * is reloaded in development).
 */
@Slf4j
class OpenApiValidationService {

    static final String PREFIX = 'grails.plugins.swagger.validation'

    GrailsApplication grailsApplication
    OpenApiService openApiService

    private volatile OpenApiValidator validator
    private volatile boolean unavailable

    /** Cached validator, or null when the plugin isn't configured (logged once). */
    OpenApiValidator getValidator() {
        OpenApiValidator current = validator
        if (current != null || unavailable) return current
        synchronized (this) {
            if (validator == null && !unavailable) {
                try {
                    validator = new OpenApiValidator(buildSpec(), buildOptions())
                } catch (Exception e) {
                    unavailable = true
                    log.error("OpenAPI validation disabled: could not build the API document: ${e.message}", e)
                }
            }
            validator
        }
    }

    /** Replaces the validator, e.g. with one built from a hand-written document in tests. */
    void setValidator(OpenApiValidator validator) {
        this.validator = validator
        this.unavailable = false
    }

    void reload() {
        synchronized (this) {
            validator = null
            unavailable = false
        }
    }

    boolean hasOperation(String method, String path) {
        getValidator()?.hasOperation(method, path) ?: false
    }

    ValidationResult validateRequest(HttpData request) {
        getValidator()?.validateRequest(request) ?: ValidationResult.notMatched()
    }

    /**
     * @param request the request being answered (method and path select the operation)
     */
    ValidationResult validateResponse(HttpData request, int status, String contentType, String body) {
        getValidator()?.validateResponse(request, status, contentType, body) ?: ValidationResult.notMatched()
    }

    boolean isEnforceRequest() { config('request.enforce', Boolean, false) }

    boolean isEnforceResponse() { config('response.enforce', Boolean, false) }

    boolean isValidateResponse() { config('response.enabled', Boolean, true) }

    ValidationOptions buildOptions() {
        new ValidationOptions(
                allowAdditionalProperties: config('additionalProperties', Boolean, true),
                allowNullValues: config('allowNullValues', Boolean, true),
                failOnUndocumentedStatus: config('failOnUndocumentedStatus', Boolean, true),
                basePath: config('basePath', String, null)
        )
    }

    private Map buildSpec() {
        String configClassName = grailsApplication.config.getProperty('grails.plugins.swagger.config')
        String packageName = grailsApplication.config.getProperty('grails.plugins.swagger.package')
        if (!configClassName) throw new IllegalStateException("Configuration 'grails.plugins.swagger.config' not found")
        if (!packageName) throw new IllegalStateException("Configuration 'grails.plugins.swagger.package' not found")

        Class configClass = Class.forName(configClassName, true, grailsApplication.classLoader)
        openApiService.makeSpec(configClass: configClass, packageName: packageName)
    }

    private <T> T config(String key, Class<T> type, T defaultValue) {
        grailsApplication.config.getProperty("${PREFIX}.${key}", type, defaultValue)
    }
}
