package test.petstore


import io.gswagger.annotations.*

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
    @ApiPathParam(name = "id", type = Integer)
    @ApiResponses([
            @ApiResponse(statusCode = 404, schema = @ApiSchema(name = "ApiResponse")),
            @ApiResponse(statusCode = 200, body = User)])
    def show = {}

    @ApiOperation(path = "/{id}", method = ApiMethod.DELETE, description = "Delete user")
    @ApiPathParam(name = "id", type = Integer)
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
    @ApiQuery(name = "filterByName", schema = @ApiParam(type = String))
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
