package io.gswagger

import io.gswagger.annotations.ApiConfig
import io.gswagger.annotations.ApiContent
import io.gswagger.annotations.ApiHeader
import io.gswagger.annotations.ApiMethod
import io.gswagger.annotations.ApiOperation
import io.gswagger.annotations.ApiParam
import io.gswagger.annotations.ApiPathParam
import io.gswagger.annotations.ApiQueries
import io.gswagger.annotations.ApiQuery
import io.gswagger.annotations.ApiRequestBody
import io.gswagger.annotations.ApiResource
import io.gswagger.annotations.ApiResponse
import io.gswagger.annotations.ApiResponses
import io.gswagger.annotations.ApiSchema
import io.gswagger.annotations.ApiSchemaField
import io.gswagger.annotations.ApiServer
import io.gswagger.core.OpenApiService
import io.gswagger.validation.HttpData
import io.gswagger.validation.OpenApiValidator
import io.gswagger.validation.ValidationOptions
import io.gswagger.validation.ValidationResult
import org.junit.jupiter.api.Test

class OpenApiValidatorTest {

    @ApiConfig(title = "Validation", version = "1", servers = @ApiServer(url = "http://localhost:8080/api"))
    static class Config {}

    enum Kind { person, company }

    @ApiSchema
    static class Address {
        @ApiSchemaField(required = true) String zipCode
        @ApiSchemaField String city
    }

    @ApiSchema
    static class Customer {
        @ApiSchemaField Long id
        @ApiSchemaField(required = true) String name
        @ApiSchemaField Integer age
        @ApiSchemaField Boolean active
        @ApiSchemaField Kind kind
        @ApiSchemaField Address address
        @ApiSchemaField List<String> tags
    }

    @ApiResource(path = "/customer", contents = @ApiContent(contentType = "application/json"))
    static class CustomerController {

        @ApiOperation(method = ApiMethod.POST)
        @ApiRequestBody(body = Customer)
        @ApiResponses([
                @ApiResponse(statusCode = 201, body = Customer),
                @ApiResponse(statusCode = 400,
                        schema = @ApiSchema(name = "Error", fields = [@ApiSchemaField(name = "message", type = String, required = true)]))])
        def save = {}

        @ApiOperation(path = "/{id}")
        @ApiPathParam(name = "id", type = Long)
        @ApiQueries([
                @ApiQuery(name = "verbose", type = Boolean),
                @ApiQuery(name = "limit", schema = @ApiParam(type = Integer, minimum = 1, maximum = 100))])
        @ApiHeader(name = "X-Tenant", required = true, type = String)
        @ApiResponse(statusCode = 200, body = Customer)
        def show = {}

        @ApiOperation(path = "/me")
        @ApiResponse(statusCode = 200, body = Customer)
        def me = {}

        @ApiOperation
        @ApiResponse(statusCode = 200, body = Customer, isList = true)
        def list = {}
    }

    private static Map spec() {
        new OpenApiService().makeSpec(configClass: Config, classes: [CustomerController])
    }

    private static OpenApiValidator validator(Map opts = [:]) {
        new OpenApiValidator(spec(), new ValidationOptions(opts))
    }

    private static HttpData post(String body, String contentType = 'application/json') {
        new HttpData().method('POST').path('/api/customer').body(contentType, body)
    }

    private static HttpData show(String id) {
        new HttpData().method('GET').path("/api/customer/$id").header('X-Tenant', '1')
    }

    private static List<String> errors(ValidationResult r) { r.errors*.toString() }

    // ---- spec generation --------------------------------------------------------------------------

    @Test
    void "required fields are emitted in the schema"() {
        def s = spec()
        assert s.components.schemas.Customer.required == ['name']
        assert s.components.schemas.Address.required == ['zipCode']
        assert s.components.schemas.Error.required == ['message']
    }

    // ---- request ----------------------------------------------------------------------------------

    @Test
    void "valid request body"() {
        def r = validator().validateRequest(post('{"name":"Ana","age":30,"kind":"person","address":{"zipCode":"01000"},"tags":["a"]}'))
        assert r.matched
        assert r.operation == 'POST /customer'
        assert r.valid, r.toString()
    }

    @Test
    void "missing required and wrong types are reported with their location"() {
        def r = validator().validateRequest(post('{"age":"thirty","active":1,"kind":"robot","address":{},"tags":[1]}'))
        assert !r.valid
        def e = errors(r)
        assert 'body.name: is required' in e
        assert e.any { it.startsWith('body.age: must be integer') }
        assert e.any { it.startsWith('body.active: must be boolean') }
        assert e.any { it.startsWith('body.kind: must be one of') }
        assert 'body.address.zipCode: is required' in e
        assert e.any { it.startsWith('body.tags[0]: must be string') }
    }

    @Test
    void "required property sent as null is an error"() {
        def r = validator().validateRequest(post('{"name":null}'))
        assert errors(r) == ['body.name: is required']
    }

    @Test
    void "optional null is accepted unless allowNullValues is false"() {
        assert validator().validateRequest(post('{"name":"Ana","age":null}')).valid
        def r = validator(allowNullValues: false).validateRequest(post('{"name":"Ana","age":null}'))
        assert errors(r) == ['body.age: must not be null']
    }

    @Test
    void "additional properties follow the configuration"() {
        String body = '{"name":"Ana","nickname":"a","address":{"zipCode":"1","extra":true}}'
        assert validator().validateRequest(post(body)).valid

        def r = validator(allowAdditionalProperties: false).validateRequest(post(body))
        assert errors(r).sort() == ['body.address.extra: is not a documented property', 'body.nickname: is not a documented property']
    }

    @Test
    void "undocumented content type and invalid JSON"() {
        def r = validator().validateRequest(post('name=Ana', 'application/x-www-form-urlencoded'))
        assert errors(r).any { it.startsWith("content-type: 'application/x-www-form-urlencoded' is not documented") }

        def r2 = validator().validateRequest(post('{"name":'))
        assert errors(r2).any { it.startsWith('body: invalid JSON') }
    }

    @Test
    void "charset in content type is ignored"() {
        assert validator().validateRequest(post('{"name":"Ana"}', 'application/json; charset=UTF-8')).valid
    }

    @Test
    void "path, query and header parameters"() {
        assert validator().validateRequest(show('10').query('verbose', 'true').query('limit', '5')).valid

        def r = validator().validateRequest(new HttpData().method('GET').path('/api/customer/abc')
                .query('verbose', 'yes').query('limit', '500'))
        def e = errors(r)
        assert e.any { it.startsWith('path.id: must be integer') }
        assert e.any { it.startsWith('query.verbose: must be boolean') }
        assert 'query.limit: must be <= 100' in e
        assert 'header.X-Tenant: is required' in e
    }

    @Test
    void "header names are case-insensitive"() {
        assert validator().validateRequest(new HttpData().method('GET').path('/api/customer/1').header('x-tenant', '1')).valid
    }

    @Test
    void "literal path wins over templated path"() {
        def r = validator().validateRequest(new HttpData().method('GET').path('/api/customer/me'))
        assert r.operation == 'GET /customer/me'
        assert r.valid
    }

    @Test
    void "undocumented routes are not validated"() {
        def v = validator()
        assert !v.validateRequest(new HttpData().method('GET').path('/api/other')).matched
        assert !v.validateRequest(new HttpData().method('DELETE').path('/api/customer')).matched
        assert !v.hasOperation('PATCH', '/api/customer/1')
        assert v.hasOperation('get', '/api/customer/1/')
    }

    @Test
    void "base path can be configured explicitly"() {
        def v = validator(basePath: '/v2')
        assert v.hasOperation('GET', '/v2/customer/1')
        assert !v.hasOperation('GET', '/api/customer/1')
    }

    // ---- response ---------------------------------------------------------------------------------

    @Test
    void "valid and invalid response bodies"() {
        def v = validator()
        def req = post('{}')
        assert v.validateResponse(req, 201, 'application/json', '{"id":1,"name":"Ana"}').valid

        def r = v.validateResponse(req, 201, 'application/json', '{"id":"x"}')
        assert errors(r).sort() == ['body.id: must be integer but was string', 'body.name: is required']

        def r400 = v.validateResponse(req, 400, 'application/json', '{"message":"bad"}')
        assert r400.valid
    }

    @Test
    void "list responses validate every item"() {
        def v = validator()
        def req = new HttpData().method('GET').path('/api/customer')
        assert v.validateResponse(req, 200, 'application/json', '[{"name":"a"},{"name":"b"}]').valid

        def r = v.validateResponse(req, 200, 'application/json', '[{"name":"a"},{"id":2}]')
        assert errors(r) == ['body[1].name: is required']

        def r2 = v.validateResponse(req, 200, 'application/json', '{"name":"a"}')
        assert errors(r2) == ['body: must be array but was object']
    }

    @Test
    void "undocumented status and missing body"() {
        def v = validator()
        def req = post('{}')
        assert errors(v.validateResponse(req, 500, 'application/json', '{}')).any { it.startsWith('status: status 500 is not documented') }
        assert validator(failOnUndocumentedStatus: false).validateResponse(req, 500, 'application/json', '{}').valid
        assert errors(v.validateResponse(req, 201, 'application/json', '')).any { it.startsWith('body: response body is required') }
    }

    // ---- schema keywords not produced by the annotations ----------------------------------------

    @Test
    void "explicit additionalProperties in the schema wins over the option"() {
        Map s = [
            paths     : ['/x': [post: [requestBody: [content: ['application/json': [schema: [
                type: 'object', properties: [a: [type: 'string']], additionalProperties: [type: 'integer']]]]]]]],
            components: [schemas: [:]]
        ]
        def v = new OpenApiValidator(s, new ValidationOptions(allowAdditionalProperties: false))
        def req = { String body -> new HttpData().method('POST').path('/x').body('application/json', body) }
        assert v.validateRequest(req('{"a":"1","b":2}')).valid
        assert errors(v.validateRequest(req('{"b":"no"}'))) == ['body.b: must be integer but was string']
    }

    @Test
    void "string formats and lengths"() {
        Map s = [paths: ['/x': [post: [requestBody: [content: ['application/json': [schema: [
            type: 'object', properties: [
                d : [type: 'string', format: 'date'],
                dt: [type: 'string', format: 'date-time'],
                e : [type: 'string', format: 'email'],
                u : [type: 'string', format: 'uuid'],
                n : [type: 'string', minLength: 2, maxLength: 3, pattern: '^[a-z]+$']]]]]]]]]]
        def v = new OpenApiValidator(s)
        def req = { String body -> new HttpData().method('POST').path('/x').body('application/json', body) }
        assert v.validateRequest(req('{"d":"2026-01-31","dt":"2026-01-31T10:00:00Z","e":"a@b.com","u":"123e4567-e89b-12d3-a456-426614174000","n":"ab"}')).valid

        def e = errors(v.validateRequest(req('{"d":"31/01/2026","dt":"2026-01-31","e":"x","u":"1","n":"ABCD"}')))
        assert e.any { it.startsWith('body.d: must be a date') }
        assert e.any { it.startsWith('body.dt: must be a date-time') }
        assert 'body.e: must be an e-mail address' in e
        assert 'body.u: must be a UUID' in e
        assert 'body.n: length must be <= 3' in e
        assert 'body.n: must match pattern ^[a-z]+$' in e
    }
}
