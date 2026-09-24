package org.grails.plugins.swagger

import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import io.gswagger.validation.HttpData
import io.gswagger.validation.OpenApiValidator
import io.gswagger.validation.ValidationResult
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper

import java.nio.charset.StandardCharsets

/**
 * Validates every request to a documented operation, and its response, with
 * {@link OpenApiValidationService}.
 *
 * <ul>
 *   <li>invalid request + request.enforce: answers 400 with the errors and doesn't call the controller;
 *       otherwise logs a warning and continues</li>
 *   <li>invalid response + response.enforce: replaces the response with a 500 with the errors;
 *       otherwise logs a warning and sends the original response</li>
 * </ul>
 * Only JSON request bodies are buffered (form bodies are left untouched so request parameters keep
 * working); undocumented routes pass through without buffering.
 */
@Slf4j
class OpenApiValidationFilter extends OncePerRequestFilter {

    OpenApiValidationService openApiValidationService

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        String path = request.requestURI.substring(request.contextPath?.length() ?: 0)

        if (!openApiValidationService.hasOperation(request.method, path)) {
            chain.doFilter(request, response)
            return
        }

        HttpServletRequest effectiveRequest = request
        String body = null
        if (OpenApiValidator.isJson(request.contentType)) {
            def cached = new CachedBodyRequestWrapper(request)
            body = cached.bodyAsString
            effectiveRequest = cached
        }

        HttpData data = toHttpData(request, path, body)

        ValidationResult requestResult = openApiValidationService.validateRequest(data)
        if (!requestResult.valid) {
            if (openApiValidationService.enforceRequest) {
                log.info("Rejected request {}: {}", requestResult.operation, requestResult.errors)
                writeErrors(response, 400, 'Request does not match the API documentation', requestResult)
                return
            }
            log.warn("Request does not match the API documentation: {}", requestResult)
        }

        if (!openApiValidationService.validateResponse) {
            chain.doFilter(effectiveRequest, response)
            return
        }

        def wrapped = new ContentCachingResponseWrapper(response)
        try {
            chain.doFilter(effectiveRequest, wrapped)

            String responseBody = new String(wrapped.contentAsByteArray, responseCharset(wrapped))
            ValidationResult responseResult = openApiValidationService.validateResponse(
                    data, wrapped.status, wrapped.contentType, responseBody)

            if (!responseResult.valid) {
                if (openApiValidationService.enforceResponse) {
                    log.error("Response does not match the API documentation: {}", responseResult)
                    wrapped.resetBuffer()
                    writeErrors(wrapped, 500, 'Response does not match the API documentation', responseResult)
                } else {
                    log.warn("Response does not match the API documentation: {}", responseResult)
                }
            }
        } finally {
            wrapped.copyBodyToResponse()
        }
    }

    static HttpData toHttpData(HttpServletRequest request, String path, String body) {
        def data = new HttpData(method: request.method, path: path, contentType: request.contentType, body: body)
        // parsed from the query string: getParameterMap() would consume form bodies
        data.query = HttpData.parseQueryString(request.queryString)
        Map<String, List<String>> headers = [:]
        request.headerNames?.each { String name -> headers[name] = request.getHeaders(name).toList() }
        data.headers = headers
        request.cookies?.each { data.cookies[it.name] = it.value }
        data
    }

    private static void writeErrors(HttpServletResponse response, int status, String message, ValidationResult result) {
        response.status = status
        response.contentType = 'application/json'
        response.characterEncoding = 'UTF-8'
        byte[] bytes = JsonOutput.toJson([message: message] + result.toMap()).getBytes(StandardCharsets.UTF_8)
        response.outputStream.write(bytes)
    }

    private static java.nio.charset.Charset responseCharset(HttpServletResponse response) {
        try {
            response.characterEncoding ? java.nio.charset.Charset.forName(response.characterEncoding) : StandardCharsets.UTF_8
        } catch (IllegalArgumentException ignored) {
            StandardCharsets.UTF_8
        }
    }
}
