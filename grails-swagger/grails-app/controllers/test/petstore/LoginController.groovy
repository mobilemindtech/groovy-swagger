package test.petstore


import io.gswagger.annotations.ApiContent
import io.gswagger.annotations.ApiOperation
import io.gswagger.annotations.ApiRequestBody
import io.gswagger.annotations.ApiResource
import io.gswagger.annotations.ApiResponse
import io.gswagger.annotations.ApiResponses
import io.gswagger.annotations.ApiSchema
import io.gswagger.annotations.ApiSchemaField
import io.gswagger.annotations.ContentType

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
