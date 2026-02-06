package test.petstore


import io.gswagger.annotations.*

@ApiResource(path = "/login", contents = @ApiContent(contentType =  ContentType.JSON))
class LoginController {

    @ApiOperation(description = "Logs user into the system")
    @ApiRequestBody(
            schema = @ApiSchema(
                    name = "AuthData",
                    fields = [
                            @ApiSchemaField(name = "username", type = String),
                            @ApiSchemaField(name = "password", type = String)
                    ]
            )
    )
    @ApiResponses([
            @ApiResponse(statusCode = 401),
            @ApiResponse(
                    statusCode = 200,
                    schema = @ApiSchema(
                            name = "TokenInfo",
                            fields = [
                                    @ApiSchemaField(name = "access_token", type = String),
                                    @ApiSchemaField(name = "expiration", type = Long)
                            ]
                    ))
    ])
    def auth = {}

}
