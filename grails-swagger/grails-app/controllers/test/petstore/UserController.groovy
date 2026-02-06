package test.petstore


import io.gswagger.annotations.ApiContent
import io.gswagger.annotations.ApiMethod
import io.gswagger.annotations.ApiOperation
import io.gswagger.annotations.ApiParam
import io.gswagger.annotations.ApiParameterSchema
import io.gswagger.annotations.ApiQuery
import io.gswagger.annotations.ApiRequestBody
import io.gswagger.annotations.ApiResource
import io.gswagger.annotations.ApiResponse
import io.gswagger.annotations.ApiResponses
import io.gswagger.annotations.ApiSchema
import io.gswagger.annotations.ApiSchemaField
import io.gswagger.annotations.ApiSecurity
import io.gswagger.annotations.ContentType

@ApiResource(path = "/user", contents = @ApiContent(contentType = ContentType.JSON))
@ApiSecurity("JWTAuth")
class UserController {

    @ApiOperation(method = ApiMethod.POST, description = "Create new user")
    @ApiRequestBody(body = User)
    @ApiResponses([
            @ApiResponse(statusCode = 400, schema = @ApiSchema(name = "ApiResponse")),
            @ApiResponse(statusCode = 200, body = User)])
    def create = {}

    @ApiOperation(path = "/{id}", description = "Show user")
    @ApiParam(name = "id", type = Integer)
    @ApiResponses([
            @ApiResponse(statusCode = 404, schema = @ApiSchema(name = "ApiResponse")),
            @ApiResponse(statusCode = 200, body = User)])
    def show = {}

    @ApiOperation(path = "/{id}", method = ApiMethod.DELETE, description = "Delete user")
    @ApiParam(name = "id", type = Integer)
    @ApiResponses([
            @ApiResponse(statusCode = 404, schema = @ApiSchema(name = "ApiResponse")),
            @ApiResponse(statusCode = 200)])
    def delete = {}

    @ApiOperation(method = ApiMethod.PUT, description = "Update user")
    @ApiRequestBody(body = User)
    @ApiResponses([
            @ApiResponse(statusCode = 400, schema = @ApiSchema(name = "ApiResponse")),
            @ApiResponse(statusCode = 404, schema = @ApiSchema(name = "ApiResponse")),
            @ApiResponse(statusCode = 200, body = User)]
    )
    def update = {}

    @ApiOperation(path = "/{id}", method = ApiMethod.DELETE, description = "List users")
    @ApiQuery(name = "filterByName", schema = @ApiParameterSchema(type = String))
    @ApiResponse(
            statusCode = 200,
            schema = @ApiSchema(
                    name = "Users",
                    fields = [
                            @ApiSchemaField(name = "total_count", type = Integer),
                            @ApiSchemaField(name = "data", isList = true, schema = @ApiSchema(name = "User"))
                    ]
            ))
    def list = {}
}
