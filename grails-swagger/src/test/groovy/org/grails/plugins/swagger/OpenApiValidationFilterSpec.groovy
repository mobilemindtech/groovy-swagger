package org.grails.plugins.swagger

import grails.testing.services.ServiceUnitTest
import groovy.json.JsonSlurper
import io.gswagger.core.OpenApiService
import io.gswagger.validation.HttpData
import io.gswagger.validation.OpenApiValidator
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

class OpenApiValidationFilterSpec extends Specification implements ServiceUnitTest<OpenApiValidationService> {

    static final Map SPEC = [
        servers   : [[url: 'http://localhost:8080/api']],
        paths     : [
            '/customer'     : [post: [
                requestBody: [content: ['application/json': [schema: ['$ref': '#/components/schemas/Customer']]]],
                responses  : ['201': [content: ['application/json': [schema: ['$ref': '#/components/schemas/Customer']]]]]
            ]],
            '/customer/{id}': [get: [
                parameters: [[name: 'id', in: 'path', schema: [type: 'integer']]],
                responses : ['200': [content: ['application/json': [schema: ['$ref': '#/components/schemas/Customer']]]]]
            ]]
        ],
        components: [schemas: [Customer: [type: 'object', required: ['name'],
                                          properties: [id: [type: 'integer'], name: [type: 'string']]]]]
    ]

    Closure doWithConfig() {{ config ->
        config.grails.plugins.swagger.config = 'test.petstore.PetStoreApiConfig'
        config.grails.plugins.swagger.package = 'test.petstore'
    }}

    OpenApiValidationFilter filter

    void setup() {
        service.validator = new OpenApiValidator(SPEC, service.buildOptions())
        filter = new OpenApiValidationFilter(openApiValidationService: service)
    }

    private void setConfig(Map<String, Object> values) {
        values.each { k, v -> config.setAt("${OpenApiValidationService.PREFIX}.${k}".toString(), v) }
        service.validator = new OpenApiValidator(SPEC, service.buildOptions())
    }

    /** runs the filter; the "controller" echoes the request body into the response */
    private Map run(MockHttpServletRequest request, int status = 201, String responseBody = null) {
        boolean[] called = [false]
        String[] seenBody = [null]
        def servlet = new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                called[0] = true
                seenBody[0] = req.reader.text
                resp.status = status
                resp.contentType = 'application/json'
                resp.writer.write(responseBody ?: seenBody[0])
            }
        }
        def response = new MockHttpServletResponse()
        filter.doFilter(request, response, new MockFilterChain(servlet))
        [response: response, called: called[0], seenBody: seenBody[0],
         json: response.contentAsString ? new JsonSlurper().parseText(response.contentAsString) : null]
    }

    private static MockHttpServletRequest post(String body) {
        def request = new MockHttpServletRequest('POST', '/api/customer')
        request.contentType = 'application/json'
        request.content = body.getBytes('UTF-8')
        request
    }

    void "valid request reaches the controller with its body intact"() {
        when:
        def r = run(post('{"name":"Ana"}'))

        then:
        r.called
        r.seenBody == '{"name":"Ana"}'
        r.response.status == 201
        r.json == [name: 'Ana']
    }

    void "invalid request is only logged when request.enforce is false (default)"() {
        when:
        def r = run(post('{"id":"x"}'), 201, '{"name":"ok"}')

        then:
        r.called
        r.response.status == 201
    }

    void "invalid request is rejected with 400 when request.enforce is true"() {
        given:
        setConfig('request.enforce': true)

        when:
        def r = run(post('{"id":"x"}'))

        then:
        !r.called
        r.response.status == 400
        r.json.message == 'Request does not match the API documentation'
        r.json.operation == 'POST /customer'
        r.json.errors*.location.sort() == ['body.id', 'body.name']
    }

    void "path parameters are validated"() {
        given:
        setConfig('request.enforce': true)

        when:
        def r = run(new MockHttpServletRequest('GET', '/api/customer/abc'))

        then:
        r.response.status == 400
        r.json.errors*.location == ['path.id']
    }

    void "additional properties follow the configuration"() {
        given:
        setConfig('request.enforce': true, additionalProperties: false)

        when:
        def r = run(post('{"name":"Ana","nick":"a"}'))

        then:
        r.response.status == 400
        r.json.errors == [[location: 'body.nick', message: 'is not a documented property']]
    }

    void "invalid response keeps the original answer when response.enforce is false (default)"() {
        when:
        def r = run(post('{"name":"Ana"}'), 201, '{"id":"not-a-number"}')

        then:
        r.response.status == 201
        r.json == [id: 'not-a-number']
    }

    void "invalid response becomes 500 when response.enforce is true"() {
        given:
        setConfig('response.enforce': true)

        when:
        def r = run(post('{"name":"Ana"}'), 201, '{"id":"not-a-number"}')

        then:
        r.response.status == 500
        r.json.message == 'Response does not match the API documentation'
        r.json.errors*.location.sort() == ['body.id', 'body.name']
    }

    void "undocumented status fails when response.enforce is true"() {
        given:
        setConfig('response.enforce': true)

        when:
        def r = run(post('{"name":"Ana"}'), 409, '{}')

        then:
        r.response.status == 500
        r.json.errors*.location == ['status']
    }

    void "undocumented routes pass through untouched"() {
        given:
        setConfig('request.enforce': true, 'response.enforce': true)
        def request = new MockHttpServletRequest('POST', '/api/other')
        request.contentType = 'application/json'
        request.content = '{"anything":1}'.bytes

        when:
        def r = run(request, 418, '{"x":1}')

        then:
        r.called
        r.response.status == 418
    }

    void "form bodies are not buffered so request parameters keep working"() {
        given:
        def request = new MockHttpServletRequest('POST', '/api/customer')
        request.contentType = 'application/x-www-form-urlencoded'
        request.addParameter('name', 'Ana')

        when:
        String[] seen = [null]
        def response = new MockHttpServletResponse()
        filter.doFilter(request, response, new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                seen[0] = req.getParameter('name')
                resp.status = 201
            }
        }))

        then:
        seen[0] == 'Ana'
    }

    void "service works without the servlet API"() {
        expect:
        service.validateRequest(new HttpData().method('POST').path('/api/customer').body('application/json', '{"name":"a"}')).valid
        !service.validateRequest(new HttpData().method('POST').path('/api/customer').body('application/json', '{}')).valid
        !service.validateRequest(new HttpData().method('GET').path('/nothing')).matched
    }

    void "service builds the document from grails.plugins.swagger.config/package"() {
        given:
        service.openApiService = new OpenApiService()
        service.reload()

        expect: "petstore sample: POST /user takes a User"
        service.validator.spec.paths.containsKey('/user/')
        service.validateRequest(new HttpData().method('POST').path('/api/user').body('application/json', '{"id":1,"username":"a"}')).valid
        service.validateRequest(new HttpData().method('POST').path('/api/user').body('application/json', '{"id":"x"}'))
                .errors*.location == ['body.id']
    }
}
