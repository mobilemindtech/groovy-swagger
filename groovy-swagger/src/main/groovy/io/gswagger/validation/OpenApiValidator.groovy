package io.gswagger.validation

import groovy.json.JsonException
import groovy.json.JsonSlurper

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Validates HTTP requests and responses against an OpenAPI document (the Map built by
 * {@link io.gswagger.core.OpenApiService#makeSpec}). Framework-independent and thread-safe: build it
 * once per document and reuse it.
 *
 * <ul>
 *   <li>Requests: path/query/header/cookie parameters (required + schema, with string-to-type
 *       conversion), request content type and JSON body.</li>
 *   <li>Responses: documented status code, content type and JSON body.</li>
 *   <li>Requests to paths/methods that aren't documented are not validated
 *       ({@link ValidationResult#isMatched()} is false).</li>
 *   <li>Security requirements are not checked (that's the security layer's job).</li>
 * </ul>
 */
class OpenApiValidator {

    final Map spec
    final ValidationOptions options
    private final SchemaValidator schemaValidator
    private final List<PathTemplate> templates
    private final List<String> basePaths

    OpenApiValidator(Map spec, ValidationOptions options = new ValidationOptions()) {
        this.spec = spec
        this.options = options
        this.schemaValidator = new SchemaValidator(spec, options)
        this.templates = buildTemplates(spec)
        this.basePaths = buildBasePaths(spec, options)
    }

    /** True when method + path match a documented operation (cheap check to skip undocumented routes). */
    boolean hasOperation(String method, String path) {
        findOperation(method, path) != null
    }

    ValidationResult validateRequest(HttpData request) {
        def match = findOperation(request.method, request.path)
        if (!match) return ValidationResult.notMatched()

        List<ValidationError> errors = []
        validateParameters(match, request, errors)
        validateRequestBody(match.operation.requestBody as Map, request, errors)
        new ValidationResult(true, match.name, errors)
    }

    /**
     * @param request the request the response answers (only method and path are used)
     */
    ValidationResult validateResponse(HttpData request, int status, String contentType, String body) {
        def match = findOperation(request.method, request.path)
        if (!match) return ValidationResult.notMatched()

        List<ValidationError> errors = []
        Map responses = (match.operation.responses ?: [:]) as Map
        Map response = responseFor(responses, status)

        if (response == null) {
            if (responses && options.failOnUndocumentedStatus)
                errors << new ValidationError('status', "status $status is not documented (documented: ${responses.keySet().join(', ')})")
        } else if (response.content instanceof Map && response.content) {
            validateContent(response.content as Map, contentType, body, true, 'body', errors)
        }
        new ValidationResult(true, match.name, errors)
    }

    // ---- operations -------------------------------------------------------------------------------

    private static class PathTemplate {
        String template
        Pattern regex
        List<String> paramNames
        int literalLength
    }

    private static class OperationMatch {
        String name
        Map operation
        Map pathItem
        Map<String, String> pathParams
    }

    private OperationMatch findOperation(String method, String rawPath) {
        if (!method || rawPath == null) return null
        String m = method.toLowerCase()
        for (String candidate : candidatePaths(rawPath)) {
            for (PathTemplate t : templates) {
                Matcher matcher = t.regex.matcher(candidate)
                if (!matcher.matches()) continue
                Map pathItem = spec.paths[t.template] as Map
                Map operation = pathItem?.get(m) as Map
                if (operation == null) continue
                Map<String, String> params = [:]
                t.paramNames.eachWithIndex { String name, int i ->
                    params[name] = URLDecoder.decode(matcher.group(i + 1), 'UTF-8')
                }
                return new OperationMatch(name: "${method.toUpperCase()} ${normalize(t.template)}", operation: operation,
                        pathItem: pathItem, pathParams: params)
            }
        }
        null
    }

    private List<String> candidatePaths(String rawPath) {
        String path = normalize(rawPath)
        List<String> result = [path]
        basePaths.each { String base ->
            if (path == base) result << '/'
            else if (path.startsWith(base + '/')) result << path.substring(base.length())
        }
        result
    }

    private static List<PathTemplate> buildTemplates(Map spec) {
        List<PathTemplate> list = ((spec?.paths ?: [:]) as Map).keySet().collect { key ->
            String template = key as String
            String normalized = normalize(template)
            List<String> names = []
            StringBuilder regex = new StringBuilder()
            int literal = 0
            Matcher m = normalized =~ /\{([^}\/]+)\}/
            int last = 0
            while (m.find()) {
                String text = normalized.substring(last, m.start())
                regex.append(Pattern.quote(text))
                literal += text.length()
                regex.append('([^/]+)')
                names << m.group(1)
                last = m.end()
            }
            String tail = normalized.substring(last)
            regex.append(Pattern.quote(tail))
            literal += tail.length()
            new PathTemplate(template: template, regex: Pattern.compile(regex.toString()),
                    paramNames: names, literalLength: literal)
        }
        // literal paths win over templated ones (/user/me before /user/{id}), then the most specific
        list.sort { a, b -> a.paramNames.size() <=> b.paramNames.size() ?: b.literalLength <=> a.literalLength }
    }

    private static List<String> buildBasePaths(Map spec, ValidationOptions options) {
        if (options.basePath != null) {
            String base = normalize(options.basePath)
            return base == '/' ? [] : [base]
        }
        ((spec?.servers ?: []) as List<Map>).collect { Map server ->
            String url = server.url as String
            if (!url) return null
            try {
                String path = url.contains('://') ? new URI(url).path : url
                path = normalize(path ?: '/')
                path == '/' ? null : path
            } catch (URISyntaxException ignored) {
                null
            }
        }.findAll().unique() as List<String>
    }

    private static String normalize(String path) {
        if (!path) return '/'
        String p = path.startsWith('/') ? path : '/' + path
        while (p.length() > 1 && p.endsWith('/')) p = p.substring(0, p.length() - 1)
        p
    }

    // ---- parameters -------------------------------------------------------------------------------

    private void validateParameters(OperationMatch match, HttpData request, List<ValidationError> errors) {
        // path-item parameters apply to every operation unless the operation overrides them (same name + in)
        Map<String, Map> byKey = [:]
        ((match.pathItem.parameters ?: []) as List<Map>).each { byKey["${it.in}:${it.name}"] = it }
        ((match.operation.parameters ?: []) as List<Map>).each { byKey["${it.in}:${it.name}"] = it }

        byKey.values().each { Map param ->
            String name = param.name
            String location = "${param.in}.${name}"
            List<String> raw = rawParameter(param.in as String, name, match, request)
            boolean required = param.required == true || param.in == 'path'

            if (raw == null || raw.isEmpty() || (raw.size() == 1 && raw[0] == '' && param.in != 'query')) {
                if (required) errors << new ValidationError(location, 'is required')
                return
            }
            if (param.schema instanceof Map) {
                def value = schemaValidator.coerceParameter(raw, param.schema as Map)
                errors.addAll(schemaValidator.validate(value, param.schema as Map, location))
            }
        }
    }

    private static List<String> rawParameter(String in, String name, OperationMatch match, HttpData request) {
        switch (in) {
            case 'path':
                String v = match.pathParams[name]
                return v == null ? null : [v]
            case 'query':
                return request.query?.get(name)
            case 'header':
                return request.headers?.get(name)
            case 'cookie':
                String c = request.cookies?.get(name)
                return c == null ? null : [c]
            default:
                return null
        }
    }

    // ---- bodies -----------------------------------------------------------------------------------

    private void validateRequestBody(Map requestBody, HttpData request, List<ValidationError> errors) {
        boolean hasBody = request.body != null && !request.body.trim().isEmpty()
        if (!requestBody) return
        if (!hasBody) {
            if (requestBody.required == true) errors << new ValidationError('body', 'request body is required')
            return
        }
        if (requestBody.content instanceof Map && requestBody.content)
            validateContent(requestBody.content as Map, request.contentType, request.body, false, 'body', errors)
    }

    private void validateContent(Map content, String contentType, String body, boolean isResponse,
                                 String location, List<ValidationError> errors) {
        boolean hasBody = body != null && !body.trim().isEmpty()
        if (!hasBody) {
            if (isResponse) errors << new ValidationError(location, "response body is required (${content.keySet().join(', ')})")
            return
        }

        String mediaType = baseMediaType(contentType)
        String key = content.keySet().find { mediaTypeMatches(it as String, mediaType) } as String
        if (key == null) {
            errors << new ValidationError('content-type',
                    "'${contentType ?: 'none'}' is not documented (expected ${content.keySet().join(', ')})")
            return
        }

        Map schema = (content[key] as Map)?.schema as Map
        if (schema == null || !isJson(mediaType ?: key)) return // only JSON bodies are schema-validated

        def parsed
        try {
            parsed = new JsonSlurper().parseText(body)
        } catch (JsonException | IllegalArgumentException e) {
            errors << new ValidationError(location, "invalid JSON: ${e.message?.readLines()?.first()}")
            return
        }
        errors.addAll(schemaValidator.validate(parsed, schema, location))
    }

    private static Map responseFor(Map responses, int status) {
        String code = status.toString()
        if (responses.containsKey(code)) return responses[code] as Map
        String range = "${code[0]}XX"
        def byRange = responses.find { k, v -> (k as String).equalsIgnoreCase(range) }
        if (byRange) return byRange.value as Map
        responses['default'] as Map
    }

    private static String baseMediaType(String contentType) {
        contentType ? contentType.split(';')[0].trim().toLowerCase() : null
    }

    private static boolean mediaTypeMatches(String documented, String actual) {
        String doc = baseMediaType(documented)
        if (doc == '*/*') return true
        if (actual == null) return false
        if (doc == actual) return true
        doc.endsWith('/*') && actual.startsWith(doc.substring(0, doc.length() - 1))
    }

    static boolean isJson(String mediaType) {
        String mt = baseMediaType(mediaType)
        mt != null && (mt == 'application/json' || mt.endsWith('+json') || mt == '*/*')
    }
}
