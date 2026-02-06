package test.petstore


import io.gswagger.annotations.ApiConfig
import io.gswagger.annotations.ApiSchema
import io.gswagger.annotations.ApiSchemaField
import io.gswagger.annotations.ApiSecurityScheme
import io.gswagger.annotations.ApiServer
import io.gswagger.annotations.SecurityType

@ApiConfig(
    title = "Pet Store",
    description = "Pet Store Swagger API demo",
    servers = [
        @ApiServer(url = "http://localhost:8080/api", description = "Development URL")
    ],
    securitySchemes = [
        @ApiSecurityScheme(name = "JWTAuth", type = SecurityType.BEARER),
    ],
    schemas = [
        @ApiSchema(
            name = "ApiResponse",
            fields = [
                @ApiSchemaField(name = "code", type = Integer),
                @ApiSchemaField(name = "type", type = String),
                @ApiSchemaField(name = "message", type = String),
            ]
        )
    ]
)
class PetStoreApiConfig {
}

enum PetStatus{
    available, pending, sold
}

enum OrderStatus{
    placed, approved, delivered
}

@ApiSchema
class Category {
    @ApiSchemaField
    int id
    @ApiSchemaField
    String name
}

@ApiSchema
class Tag {
    @ApiSchemaField
    int id
    @ApiSchemaField
    String name
}

@ApiSchema
class Pet {
    @ApiSchemaField
    int id
    @ApiSchemaField(required = true)
    String name
    @ApiSchemaField
    Category category
    @ApiSchemaField
    List<String> photoUrls
    @ApiSchemaField
    List<Tag> tags
    @ApiSchemaField
    PetStatus status
}

@ApiSchema
class Order {
    @ApiSchemaField
    int id
    @ApiSchemaField
    int petId
    @ApiSchemaField
    int quantity
    @ApiSchemaField(example = "yyyy-MM-dd")
    int shipDate
    @ApiSchemaField
    OrderStatus status
    @ApiSchemaField
    Boolean complete
}

@ApiSchema
class User {
    @ApiSchemaField
    int id
    @ApiSchemaField
    String username
    @ApiSchemaField
    String firstName
    @ApiSchemaField
    String lastName
    @ApiSchemaField
    String email
    @ApiSchemaField
    String password
    @ApiSchemaField
    String phone
    @ApiSchemaField(options = ["active", "inactive"])
    String userStatus
}