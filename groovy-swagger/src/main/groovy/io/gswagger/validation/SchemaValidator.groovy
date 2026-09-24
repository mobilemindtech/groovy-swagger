package io.gswagger.validation

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Validates already-parsed JSON values (Map / List / String / Number / Boolean / null, as produced by
 * JsonSlurper) against OpenAPI schema objects.
 *
 * Supported keywords: `$ref` (#/components/schemas/...), `type` (string or list, incl. "null"),
 * `nullable`, `enum`, `properties`, `required`, `additionalProperties` (boolean or schema), `items`,
 * `minLength`, `maxLength`, `pattern`, `format` (date, date-time, email, uuid, int32, int64),
 * `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `minItems`, `maxItems`, `uniqueItems`,
 * `allOf`, `anyOf`, `oneOf`. Unknown keywords are ignored.
 */
class SchemaValidator {

    private static final String REF_PREFIX = '#/components/schemas/'
    private static final Pattern EMAIL = ~/^[^@\s]+@[^@\s]+\.[^@\s]+$/
    private static final Pattern UUID_PATTERN = ~/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/

    private final Map<String, Map> schemas
    private final ValidationOptions options
    private final Map<String, Pattern> patternCache = [:].asSynchronized()

    SchemaValidator(Map spec, ValidationOptions options) {
        this.schemas = (spec?.components?.schemas ?: [:]) as Map<String, Map>
        this.options = options
    }

    List<ValidationError> validate(Object value, Map schema, String location) {
        List<ValidationError> errors = []
        validateInto(value, schema, location, errors, 0)
        errors
    }

    /** Resolves `$ref` chains; returns the schema itself when it has no `$ref`. */
    Map resolve(Map schema) {
        Map current = schema
        int guard = 0
        while (current?.'$ref') {
            String ref = current.'$ref'
            if (!ref.startsWith(REF_PREFIX) || ++guard > 32)
                throw new IllegalArgumentException("Unsupported schema reference: $ref")
            String name = ref.substring(REF_PREFIX.length())
            current = schemas[name]
            if (current == null)
                throw new IllegalArgumentException("Schema not found: $ref")
        }
        current
    }

    /**
     * Converts a parameter string (path/query/header/cookie) to the type its schema declares, so the
     * value can be validated like a JSON value. Returns the raw string when no conversion applies.
     */
    Object coerceParameter(List<String> rawValues, Map schema) {
        Map resolved = resolve(schema) ?: [:]
        List<String> types = typesOf(resolved)
        if ('array' in types) {
            List<String> parts = rawValues.size() == 1 ? rawValues[0].split(',', -1).toList() : rawValues
            Map itemSchema = resolved.items instanceof Map ? resolved.items as Map : [:]
            return parts.collect { coerceScalar(it, itemSchema) }
        }
        coerceScalar(rawValues ? rawValues[0] : null, resolved)
    }

    private Object coerceScalar(String raw, Map schema) {
        if (raw == null) return null
        List<String> types = typesOf(resolve(schema) ?: [:])
        try {
            if ('integer' in types) return new BigInteger(raw.trim())
            if ('number' in types) return new BigDecimal(raw.trim())
        } catch (NumberFormatException ignored) {
            return raw // type check reports it
        }
        if ('boolean' in types && (raw == 'true' || raw == 'false')) return Boolean.valueOf(raw)
        raw
    }

    private void validateInto(Object value, Map schema, String location, List<ValidationError> errors, int depth) {
        if (schema == null) return
        if (depth > 64) {
            errors << new ValidationError(location, 'maximum nesting depth exceeded')
            return
        }

        Map s
        try {
            s = resolve(schema)
        } catch (IllegalArgumentException e) {
            errors << new ValidationError(location, e.message)
            return
        }

        if (s.allOf instanceof List) {
            (s.allOf as List<Map>).each { validateInto(value, it, location, errors, depth + 1) }
        }
        if (s.anyOf instanceof List) {
            boolean ok = (s.anyOf as List<Map>).any { validate(value, it, location).isEmpty() }
            if (!ok) errors << new ValidationError(location, 'does not match any of the allowed schemas (anyOf)')
        }
        if (s.oneOf instanceof List) {
            int matches = (s.oneOf as List<Map>).count { validate(value, it, location).isEmpty() } as int
            if (matches != 1) errors << new ValidationError(location, "must match exactly one schema (oneOf), matched $matches")
        }

        List<String> types = typesOf(s)

        if (value == null) {
            if (types && !('null' in types) && !s.nullable && !options.allowNullValues)
                errors << new ValidationError(location, 'must not be null')
            return
        }

        if (types && !types.any { matchesType(value, it) }) {
            errors << new ValidationError(location, "must be ${types.join(' or ')} but was ${describe(value)}")
            return
        }

        // the in-memory document holds Java arrays here (enum constants / String[] options)
        def enumValues = s.get('enum')
        if (enumValues instanceof Object[]) enumValues = (enumValues as Object[]).toList()
        if (enumValues instanceof Collection && enumValues) {
            def allowed = (enumValues as Collection).collect { it?.toString() }
            if (!(value.toString() in allowed))
                errors << new ValidationError(location, "must be one of ${allowed} but was '${value}'")
        }

        if (value instanceof CharSequence) validateString(value.toString(), s, location, errors)
        else if (value instanceof Number) validateNumber(value as Number, s, location, errors)
        else if (value instanceof Map) validateObject(value as Map, s, location, errors, depth)
        else if (value instanceof List) validateArray(value as List, s, location, errors, depth)
    }

    private void validateString(String value, Map s, String location, List<ValidationError> errors) {
        if (s.minLength instanceof Number && value.length() < (s.minLength as int))
            errors << new ValidationError(location, "length must be >= ${s.minLength}")
        if (s.maxLength instanceof Number && value.length() > (s.maxLength as int))
            errors << new ValidationError(location, "length must be <= ${s.maxLength}")
        if (s.pattern) {
            Pattern p = compile(s.pattern as String)
            if (p == null) errors << new ValidationError(location, "invalid pattern in documentation: ${s.pattern}")
            else if (!p.matcher(value).find()) errors << new ValidationError(location, "must match pattern ${s.pattern}")
        }
        switch (s.format) {
            case 'date':
                if (!parses { LocalDate.parse(value) }) errors << new ValidationError(location, 'must be a date (yyyy-MM-dd)')
                break
            case 'date-time':
                if (!parses { OffsetDateTime.parse(value) })
                    errors << new ValidationError(location, 'must be a date-time (RFC 3339, e.g. 2026-01-31T10:00:00Z)')
                break
            case 'email':
                if (!EMAIL.matcher(value).matches()) errors << new ValidationError(location, 'must be an e-mail address')
                break
            case 'uuid':
                if (!UUID_PATTERN.matcher(value).matches()) errors << new ValidationError(location, 'must be a UUID')
                break
        }
    }

    private void validateNumber(Number value, Map s, String location, List<ValidationError> errors) {
        BigDecimal n = toBigDecimal(value)
        if (s.minimum instanceof Number) {
            BigDecimal min = toBigDecimal(s.minimum as Number)
            boolean exclusive = s.exclusiveMinimum == true
            if (exclusive ? n <= min : n < min) errors << new ValidationError(location, "must be ${exclusive ? '>' : '>='} ${s.minimum}")
        }
        if (s.exclusiveMinimum instanceof Number && n <= toBigDecimal(s.exclusiveMinimum as Number))
            errors << new ValidationError(location, "must be > ${s.exclusiveMinimum}")
        if (s.maximum instanceof Number) {
            BigDecimal max = toBigDecimal(s.maximum as Number)
            boolean exclusive = s.exclusiveMaximum == true
            if (exclusive ? n >= max : n > max) errors << new ValidationError(location, "must be ${exclusive ? '<' : '<='} ${s.maximum}")
        }
        if (s.exclusiveMaximum instanceof Number && n >= toBigDecimal(s.exclusiveMaximum as Number))
            errors << new ValidationError(location, "must be < ${s.exclusiveMaximum}")
        if (s.format == 'int32' && (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE))
            errors << new ValidationError(location, 'must fit in int32')
        if (s.format == 'int64' && (n < Long.MIN_VALUE || n > Long.MAX_VALUE))
            errors << new ValidationError(location, 'must fit in int64')
    }

    private void validateObject(Map value, Map s, String location, List<ValidationError> errors, int depth) {
        // s.get('properties'): in Groovy 5 `s.properties` on a Map returns the bean properties, not the key
        def declared = s.get('properties')
        Map<String, Map> properties = (declared instanceof Map ? declared : [:]) as Map<String, Map>
        // `required` must be a list; the generator also puts a boolean `required` in parameter schemas
        List<String> required = s.required instanceof List ? s.required as List<String> : []

        required.each { String name ->
            if (!value.containsKey(name) || value[name] == null)
                errors << new ValidationError(child(location, name), 'is required')
        }

        value.each { key, propertyValue ->
            String name = key as String
            if (properties.containsKey(name)) {
                if (propertyValue != null || !(name in required))
                    validateInto(propertyValue, properties[name], child(location, name), errors, depth + 1)
                return
            }
            def additional = s.additionalProperties
            if (additional instanceof Map) {
                validateInto(propertyValue, additional as Map, child(location, name), errors, depth + 1)
            } else {
                boolean allowed = additional instanceof Boolean ? additional as boolean : options.allowAdditionalProperties
                if (!allowed) errors << new ValidationError(child(location, name), 'is not a documented property')
            }
        }
    }

    private void validateArray(List value, Map s, String location, List<ValidationError> errors, int depth) {
        if (s.minItems instanceof Number && value.size() < (s.minItems as int))
            errors << new ValidationError(location, "must have at least ${s.minItems} items")
        if (s.maxItems instanceof Number && value.size() > (s.maxItems as int))
            errors << new ValidationError(location, "must have at most ${s.maxItems} items")
        if (s.uniqueItems == true && value.toSet().size() != value.size())
            errors << new ValidationError(location, 'items must be unique')
        if (s.items instanceof Map) {
            value.eachWithIndex { item, int i ->
                validateInto(item, s.items as Map, "${location}[${i}]", errors, depth + 1)
            }
        }
    }

    static List<String> typesOf(Map schema) {
        def type = schema?.type
        if (type instanceof Collection) return (type as Collection).collect { it as String }
        type ? [type as String] : []
    }

    private static boolean matchesType(Object value, String type) {
        switch (type) {
            case 'string': return value instanceof CharSequence
            case 'integer':
                return value instanceof Integer || value instanceof Long || value instanceof Short ||
                        value instanceof Byte || value instanceof BigInteger ||
                        (value instanceof BigDecimal && isWhole(value as BigDecimal))
            case 'number': return value instanceof Number
            case 'boolean': return value instanceof Boolean
            case 'object': return value instanceof Map
            case 'array': return value instanceof List
            case 'null': return value == null
            default: return true
        }
    }

    private static boolean isWhole(BigDecimal value) {
        value.signum() == 0 || value.scale() <= 0 || value.stripTrailingZeros().scale() <= 0
    }

    private static BigDecimal toBigDecimal(Number n) {
        n instanceof BigDecimal ? n as BigDecimal : new BigDecimal(n.toString())
    }

    private static String describe(Object value) {
        switch (value) {
            case CharSequence: return 'string'
            case Boolean: return 'boolean'
            case Number: return 'number'
            case Map: return 'object'
            case List: return 'array'
            default: return value.getClass().simpleName
        }
    }

    private static String child(String location, String name) {
        location ? "${location}.${name}" : name
    }

    private static boolean parses(Closure c) {
        try {
            c()
            true
        } catch (DateTimeParseException ignored) {
            false
        }
    }

    private Pattern compile(String regex) {
        if (patternCache.containsKey(regex)) return patternCache[regex]
        Pattern p
        try {
            p = Pattern.compile(regex)
        } catch (PatternSyntaxException ignored) {
            p = null
        }
        patternCache[regex] = p
        p
    }
}
