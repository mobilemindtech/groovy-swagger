package io.gswagger

import io.gswagger.annotations.ApiConfig
import io.gswagger.annotations.ApiContent
import io.gswagger.annotations.ApiHeader
import io.gswagger.annotations.ApiMethod
import io.gswagger.annotations.ApiOperation
import io.gswagger.annotations.ApiParam
import io.gswagger.annotations.ApiParameterSchema
import io.gswagger.annotations.ApiParams
import io.gswagger.annotations.ApiQueries
import io.gswagger.annotations.ApiQuery
import io.gswagger.annotations.ApiRequestBody
import io.gswagger.annotations.ApiResource
import io.gswagger.annotations.ApiResponse
import io.gswagger.annotations.ApiResponses
import io.gswagger.annotations.ApiSchema
import io.gswagger.annotations.ApiSchemaField
import io.gswagger.annotations.ApiSecurity
import io.gswagger.annotations.ApiSecurityScheme
import io.gswagger.annotations.ApiServer
import io.gswagger.annotations.SecurityType
import io.gswagger.core.OpenApiService
import org.junit.jupiter.api.Test

class OpenApiServiceTest {

    @ApiConfig(
        title = "User Management",
        version = "1.0.0",
        securitySchemes = [
            @ApiSecurityScheme(name = "JWTAuth", type = SecurityType.BEARER),
            @ApiSecurityScheme(name = "BasicAuth", type = SecurityType.BASIC),
            @ApiSecurityScheme(name = "NoAuth", type = SecurityType.NONE),
        ],
        schemas = @ApiSchema(
                name = "Role2",
                fields = [
                        @ApiSchemaField(name = "authority", type = String)
                ]
        ),
        servers = [
            @ApiServer(url = "http://localhost:8080")
        ]
    )
    class Config {}

    enum UserType {
        Person, Company
    }

    @ApiSchema(description = "User details")
    class User {
        @ApiSchemaField(required = true) Long id
        @ApiSchemaField String name
        @ApiSchemaField Set<Role> roles
        @ApiSchemaField Role role
        @ApiSchemaField List<String> keys
        @ApiSchemaField UserType userType
    }

    @ApiSchema(description = "Role details")
    class Role {
        @ApiSchemaField
        String authority
    }



    @ApiResource(path = "/api", contents = @ApiContent(contentType = "application/json"))
    class LoginController {

        @ApiOperation(path = "/login", method = ApiMethod.POST)
        @ApiRequestBody(
            schema = @ApiSchema(
                name = "AuthData",
                fields = [
                    @ApiSchemaField(name = "username", type = String, required = true),
                    @ApiSchemaField(name = "password", type = String, required = true),
                ]
            )
        )
        @ApiResponses([
            @ApiResponse(statusCode = 401),
            @ApiResponse(
                 statusCode = 200,
                 schema = @ApiSchema(
                     name = "AuthToken",
                     fields = [
                         @ApiSchemaField(name = "access_token", type = String, required = true),
                     ]
                )
            )]
        )
        def login = {}
    }

    @ApiResource(path = "/api/user", contents = @ApiContent(contentType = "application/json"))
    @ApiHeader(name = "X-AUTH", description = "Custom auth token", schema = @ApiParameterSchema(format = "uuid", type = String))
    @ApiSecurity("JWTAuth")
    class UserController {
        @ApiOperation(method = ApiMethod.POST)
        @ApiResponses([
            @ApiResponse(statusCode = 200, body = User),
            @ApiResponse(statusCode = 400)
        ])
        @ApiRequestBody(body = User)
        def save = {}

        @ApiOperation(path = "/save", method = ApiMethod.POST)
        @ApiResponses([
            @ApiResponse(statusCode = 200, body = User),
            @ApiResponse(statusCode = 400)
        ])
        @ApiRequestBody(
            schema = @ApiSchema(
                name = "User2",
                fields = [
                    @ApiSchemaField(name = "id", type = Integer),
                    @ApiSchemaField(name = "name", type = String),
                    @ApiSchemaField(name = "role", schema =  @ApiSchema(name = "Role2")),
                    @ApiSchemaField(name = "roles", schema = @ApiSchema(name = "Role2"), isList = true),
                    @ApiSchemaField(name = "keys", type = String, isList = true),
                    @ApiSchemaField(name = "userType", type = UserType),
                    @ApiSchemaField(name = "userType2", type = String, options = ["User1", "User2"]),
                ]
            )
        )
        def save2 = {}

        @ApiOperation(path = "/{id}", method = ApiMethod.GET)
        @ApiParams(@ApiParam(name = "id", type = Integer))
        @ApiQueries(@ApiQuery(name = "verbose", type = Boolean))
        @ApiResponse(statusCode = 200, body = User)
        def getOne = {}


        @ApiOperation(method = ApiMethod.GET)
        @ApiQueries(@ApiQuery(name = "name", type = String))
        @ApiResponse(statusCode = 200, body = User, isList = true)
        def list = {}
    }


    // security

    @Test
    void "Gerar JSON e Validar Estrutura"() {
        def service = new OpenApiService()
        String json = service.generateJsonScan(Config, "io.gswagger")

        println json

        assert json.contains('"/api/user/{id}"')
        assert json.contains('"get"')
        assert json.contains('"post"')
        assert json.contains('"#/components/schemas/User"')
        assert json.contains('"#/components/schemas/AuthData"')
        assert json.contains('"#/components/schemas/AuthToken"')
    }

}
