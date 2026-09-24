package io.gswagger.validation

import groovy.transform.CompileStatic

/**
 * Framework-independent view of an HTTP request (or of the request a response belongs to).
 * Header names are case-insensitive. The body is the raw text; only JSON bodies are schema-validated.
 */
@CompileStatic
class HttpData {
    String method
    /** request path without context path and query string, e.g. `/api/user/10` */
    String path
    Map<String, List<String>> query = [:]
    Map<String, List<String>> headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER)
    Map<String, String> cookies = [:]
    String contentType
    String body

    HttpData method(String method) { this.method = method; this }

    HttpData path(String path) { this.path = path; this }

    HttpData query(String name, String... values) { query.computeIfAbsent(name, { [] }).addAll(values); this }

    HttpData header(String name, String... values) { headers.computeIfAbsent(name, { [] }).addAll(values); this }

    HttpData cookie(String name, String value) { cookies[name] = value; this }

    HttpData body(String contentType, String body) { this.contentType = contentType; this.body = body; this }

    void setHeaders(Map<String, List<String>> headers) {
        this.headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER)
        if (headers) this.headers.putAll(headers)
    }

    /** Parses `a=1&b=2&b=3` (URL-decoded, UTF-8) */
    static Map<String, List<String>> parseQueryString(String queryString) {
        Map<String, List<String>> result = [:]
        if (!queryString) return result
        queryString.split('&').each { String pair ->
            if (!pair) return
            int idx = pair.indexOf('=')
            String name = decode(idx >= 0 ? pair.substring(0, idx) : pair)
            String value = idx >= 0 ? decode(pair.substring(idx + 1)) : ''
            result.computeIfAbsent(name, { [] }) << value
        }
        result
    }

    private static String decode(String value) {
        URLDecoder.decode(value, 'UTF-8')
    }
}
