# Groovy Swagger

`groovy-swagger` is a small Groovy-first library to **describe HTTP endpoints and data models using annotations**, then **generate an OpenAPI JSON** document at runtime by scanning your controllers (or by passing controller classes explicitly).

It’s designed to be framework-light: the annotations live in `io.gswagger.annotations.*`, and the JSON generator is `io.gswagger.core.OpenApiService`.

---

## What you get

- Annotate controllers/resources (`@ApiResource`) and operations (`@ApiOperation`)
- Describe request bodies (`@ApiRequestBody`) and responses (`@ApiResponse`, `@ApiResponses`)
- Describe parameters in **path/query/header/cookie** (`@ApiPathParam`, `@ApiQuery`, `@ApiHeader`, `@ApiCookie`)
- Define schemas:
    - by annotating classes with `@ApiSchema` and fields with `@ApiSchemaField`, or
    - inline with `@ApiSchema(fields=[...])` inside request/response annotations
- Define security schemes globally and apply them per resource/operation (`@ApiSecurityScheme`, `@ApiSecurity`)
- Multiple content types per resource/operation (`@ApiContent`)

---

## Installation (Gradle)

This repo contains multiple modules. The typical idea is:

- **groovy-swagger**: the core annotations + generator
- **grails-swagger**: Grails integration (serving and wiring the OpenAPI output inside a Grails app)

Add the module you need as a dependency.

```groovy
groovy dependencies { implementation "io.groovy.swagger:groovy-swagger:0.0.1" }
```

If you use grails

```groovy
groovy dependencies { implementation "org.grails.plugins:grails-swagger:0.0.1" }
```

---

## Quick start: generating OpenAPI JSON

### 1) Create an API config class

Annotate a config class with `@ApiConfig`. This defines OpenAPI metadata and (optionally) global components like security schemes, servers, reusable schemas, and default contents.

```groovy
import io.gswagger.annotations.*
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
class PetStoreConfig {}
```
#### 2) Schemas

```groovy
import io.gswagger.annotations.*

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
```

### 3) Annotate controllers
```groovy

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
```


### 3) Generate JSON

To generate the JSON manually:

```groovy
import io.gswagger.core.OpenApiService

def service = new OpenApiService()
String jsonData = service.generateJsonScan(PetStoreConfig, "my.controllers.package")
println jsonData

```

Using the Grails plugin, we don't need to generate it manually. 
We only need to create the plugin settings in `application.yaml`, 
and then we'll have a `/swagger` endpoint available in our application. 

```yaml 
grails:
  plugins:
    swagger:
      config: my.controllers.package.PetStoreConfig
      package: my.controllers.package
```

Now, my app will be the `/swagger` endpoint. 

---

## Modeling schemas with annotated classes

You can define a reusable schema by annotating a class with `@ApiSchema` and its fields with `@ApiSchemaField`.The generator registers schemas into:

---

## Content types

Use `@ApiContent(contentType = ...)` to define media types (like JSON, XML, etc.).

Convenience constants are provided:

- `ContentType.JSON` → `application/json`
- `ContentType.TEXT` → `text/plain`
- `ContentType.HTML` → `text/html`
- `ContentType.XML` → `text/xml`
- `ContentType.STREAM` → `application/octet-stream`

You can apply content types at different levels:

- global default in `@ApiConfig(contents=[...])`
- resource default in `@ApiResource(contents=[...])`
- per request/response via `@ApiRequestBody(contents=[...])`, `@ApiResponse(contents=[...])`
- per response group via `@ApiResponses(contents=[...])`

---

# Grails Swagger

`grails-swagger` is the Grails-friendly layer on top of `groovy-swagger`.

Typical responsibilities (depending on how your module is implemented):

- exposing an endpoint like `GET /openapi.json`
- wiring the generator (`OpenApiService`) into the Grails app context
- scanning the application controllers package automatically
- optionally serving Swagger UI (if included)

Because Grails wiring can vary by app setup, the stable part is still the same:
**you annotate controllers with `io.gswagger.annotations.*` and generate JSON via `OpenApiService`.**

A common pattern is to:

- place an `@ApiConfig` class in your Grails app
- annotate Grails controllers with `@ApiResource`, `@ApiOperation`, etc.
- expose the generated JSON via a controller action

---

# Annotation reference

This section explains **all** annotations and supporting enums/classes in `io.gswagger.annotations`.

## Enums

### `ApiMethod`
HTTP method for an operation:

- `GET`, `PUT`, `POST`, `DELETE`, `PATCH`, `HEAD`, `OPTIONS`

Used by: `@ApiOperation(method = ...)`

### `SecurityType`
Type of security scheme:

- `BEARER` (HTTP bearer, typically JWT)
- `BASIC` (HTTP basic auth)
- `API_KEY` (API key in header)
- `NONE` (special value used to indicate “no auth”; see `@ApiSecurityScheme` + `@ApiSecurity` behavior)

Used by: `@ApiSecurityScheme(anme = ...)` and `@ApiSecurity("SecName")`

---

## Helper class

### `ContentType`
String constants for media types:

- `ContentType.JSON` = `application/json`
- `ContentType.TEXT` = `text/plain`
- `ContentType.HTML` = `text/html`
- `ContentType.XML` = `text/xml`
- `ContentType.STREAM` = `application/octet-stream`

Used by: `@ApiContent(contentType = ...)`

---

## API-level configuration

### `@ApiConfig` *(TYPE)*
Defines OpenAPI document metadata and global defaults/components.

Fields:

- `openapi()` *(String, default `"3.1.1"`)*: OpenAPI version string.
- `title()` *(String)*: API title.
- `version()` *(String)*: API version.
- `description()` *(String)*: API description.
- `securitySchemes()` *(ApiSecurityScheme[])*: global security scheme definitions.
- `schemas()` *(ApiSchema[])*: reusable schema definitions (inline).
- `servers()` *(ApiServer[])*: server list.
- `contents()` *(ApiContent[])*: default content types.

Usage: 
### `@ApiServer` *(annotation)*
A single server entry.

Fields:

- `url()` *(String, required)*: base URL.
- `description()` *(String)*: description.

Used by: `@ApiConfig(servers=...)` and `@ApiServers`

### `@ApiServers` *(TYPE)*
Container to attach multiple `@ApiServer` entries as a single annotation.

Fields:

- `value()` *(ApiServer[])*

---

## Schemas (components)

### `@ApiSchema` *(TYPE)*
Defines a reusable schema component or an inline schema reference.

Fields:

- `name()` *(String)*: schema name in `components.schemas`. If empty, generator often falls back to class name.
- `type()` *(Class, default `Void`)*: optional associated type (not always used depending on generation path).
- `isList()` *(boolean)*: when used as a reference, indicates an array wrapper.
- `description()` *(String)*: schema description.
- `since()` *(String)*: informational metadata (currently not guaranteed to appear in output).
- `example()` *(String)*: informational metadata (not guaranteed to appear in output everywhere).
- `fields()` *(ApiSchemaField[])*: inline field definitions.

Typical usage patterns:

### `@ApiSchemaField` *(FIELD)*
Describes a property of a schema (used either on real class fields or inside an inline `@ApiSchema(fields=[...])`).

Fields:

- `name()` *(String)*: property name (defaults to field name when applied to a class field).
- `description()` *(String)*
- `since()` *(String)*
- `example()` *(String)*
- `type()` *(Class, default `Void`)*: property type (primitive/string/number or complex type).
- `schema()` *(ApiSchema)*: reference to another schema by name.
- `required()` *(boolean)*: indicates required (note: output handling depends on generator implementation).
- `isList()` *(boolean)*: whether the property is an array.
- `options()` *(String[])*: enum options (or overrides enum constants).

Notes:
- If `type` is an enum, generator outputs a string with `enum`.
- If `schema` is set, the generator typically emits a `$ref`.

If you are creating a new `ApiSchema` on `ApiConfig`, is REQUIRED define `type`OR `schema`. 

---

## Resources (controllers)

### `@ApiResource` *(TYPE)*
Marks a class as an API resource/controller and defines its base path.

Fields:

- `path()` *(String, required)*: base path for all operations in the class.
- `description()` *(String)*
- `since()` *(String)*
- `version()` *(String)*
- `contents()` *(ApiContent[])*: default content types for this resource.

---

## Parameters schema helper

### `@ApiParam` *(TYPE target in source; used as nested annotation)*
Describes the schema of a parameter (query/path/header/cookie). Think of it as “JSON Schema-ish” constraints for parameters.

Fields:

- `title()` *(String)*
- `description()` *(String)*
- `type()` *(Class, default `Void`)*: parameter type (required unless you provide a direct `type` on the parameter annotation).
- `typeItem()` *(Class, default `Void`)*: item type when `type` is a list/array.
- `maximum()` / `minimum()` *(int, default `-1`)*: numeric constraints.
- `maxLength()` / `minLength()` *(int, default `-1`)*: string constraints.
- `pattern()` *(String)*: regex pattern.
- `maxItems()` / `minItems()` *(int, default `-1`)*: array constraints.
- `uniqueItems()` *(boolean)*
- `required()` *(boolean)*
- `options()` *(String[])*: enum-like list of allowed values.
- `format()` *(String)*: OpenAPI format (e.g., `"int64"`, `"date-time"`, etc.).
- `defaultValue()` *(String)*: default value.

Used inside: `@ApiPathParam`, `@ApiQuery`, `@ApiHeader`, `@ApiCookie`

---

## Path parameters

### `@ApiPathParam` *(FIELD, METHOD)*
Declares a **path parameter** (OpenAPI `in: path`).

Fields:

- `name()` *(String, required)*
- `schema()` *(ApiParam)*: rich schema details.
- `type()` *(Class, default `Void`)*: shorthand type; if set, generator uses it directly.
- `description()` *(String)*
- `since()` *(String)*
- `example()` *(String)*
- `required()` *(boolean, default `true`)*: path parameters are usually required.
- `allowEmptyValue()` *(boolean)*


### `@ApiPathParams` *(FIELD, METHOD)*
Container for multiple `@ApiPathParam`.

Fields:

- `value()` *(ApiPathParam[])*

---

## Cookie parameters

### `@ApiCookie` *(FIELD, METHOD)*
Declares a **cookie parameter** (OpenAPI `in: cookie`).

Fields:

- `name()` *(String, required)*
- `schema()` *(ApiParam)*
- `type()` *(Class, default `Void`)*
- `description()` *(String)*
- `example()` *(String)*
- `since()` *(String)*
- `required()` *(boolean, default `false`)*
- `allowEmptyValue()` *(boolean, default `true`)*

### `@ApiCookies` *(FIELD, METHOD)*
Container for multiple `@ApiCookie`.

Fields:

- `value()` *(ApiCookie[])*

---

## Query parameters

### `@ApiQuery` *(FIELD, METHOD)*
Declares a **query parameter** (OpenAPI `in: query`).

Fields:

- `name()` *(String, required)*
- `schema()` *(ApiParam)*
- `type()` *(Class, default `Void`)*
- `description()` *(String)*
- `required()` *(boolean, default `false`)*
- `example()` *(String)*
- `since()` *(String)*
- `allowEmptyValue()` *(boolean, default `true`)*


### `@ApiQueries` *(FIELD, METHOD)*
Container for multiple `@ApiQuery`.

Fields:

- `value()` *(ApiQuery[])*

---

## Content (media types)

### `@ApiContent` *(FIELD, METHOD)*
Declares a single media type.

Fields:

- `contentType()` *(String, required)*

Used by:
- `@ApiConfig(contents=...)`
- `@ApiResource(contents=...)`
- `@ApiRequestBody(contents=...)`
- `@ApiResponse(contents=...)`
- `@ApiResponses(contents=...)`

---

## Headers

### `@ApiHeader` *(FIELD, TYPE, METHOD)*
Declares a **header parameter** (OpenAPI `in: header`).

Fields:

- `name()` *(String, required)*
- `description()` *(String)*
- `required()` *(boolean)*
- `example()` *(String)*
- `schema()` *(ApiParam)*
- `type()` *(Class, default `Void`)*
- `since()` *(String)*

You can place it:
- on the controller (applies to all operations)
- on an operation field (applies only to that operation)

### `@ApiHeaders` *(FIELD, TYPE, METHOD)*
Container for multiple `@ApiHeader`.

Fields:
- `value()` *(ApiHeader[])*

---

## Request body

### `@ApiRequestBody` *(FIELD, METHOD)*
Describes the request body for an operation.

Fields:

- `body()` *(Class, default `Void`)*: model class annotated with `@ApiSchema`.
- `schema()` *(ApiSchema)*: inline schema definition or schema reference by name.
- `since()` *(String)*
- `description()` *(String)*
- `isList()` *(boolean)*: if true, wraps schema as an array.
- `contents()` *(ApiContent[])*: overrides content types for this request body.

Notes:
- Use **either** `body=SomeClass` **or** `schema=@ApiSchema(...)`.
- If neither is set, the generator will output only description (no `content`).

---

## Responses

### `@ApiResponse` *(FIELD, METHOD)*
Declares a single response for an operation.

Fields:

- `statusCode()` *(int, required)*: HTTP status.
- `body()` *(Class, default `Void`)*: model class annotated with `@ApiSchema`.
- `schema()` *(ApiSchema)*: inline schema definition or reference by name.
- `description()` *(String)*
- `isList()` *(boolean)*: array wrapper.
- `contents()` *(ApiContent[])*: overrides content types for this specific response.

### `@ApiResponses` *(FIELD, METHOD)*
Container for multiple responses, plus optional default content types for the group.

Fields:

- `value()` *(ApiResponse[])*: list of responses.
- `contents()` *(ApiContent[])*: content types used when individual `@ApiResponse(contents=...)` is empty.

---

## Operation

### `@ApiOperation` *(FIELD, METHOD)*
Marks a controller member as an API operation and defines method/path metadata.

Fields:

- `path()` *(String, default `"/"`)*: operation path appended to resource path.
- `method()` *(ApiMethod, default `GET`)*
- `description()` *(String)*
- `summary()` *(String)*
- `deprecated()` *(boolean)*

---

## Security

### `@ApiSecurityScheme` *(TYPE)*
Defines a reusable security scheme under `components.securitySchemes`.

Fields:

- `name()` *(String, required)*: scheme name (reference key).
- `type()` *(SecurityType, required)*: BEARER/BASIC/API_KEY/NONE.
- `headerName()` *(String)*: required when `type=API_KEY` (header key name).
- `description()` *(String)*

Usage:
### `@ApiSecurity` *(TYPE, FIELD)*
Applies a previously-defined scheme to a resource or operation.

Fields:

- `value()` *(String, required)*: scheme name (must match `@ApiSecurityScheme(name=...)`)
- `scopes()` *(String[])*: scopes (useful for OAuth-like semantics; also fine as metadata)

Usage:
#### Special note about `SecurityType.NONE`
If you define a scheme with `type = SecurityType.NONE`, and then annotate an operation with `@ApiSecurity("<that NONE scheme name>")`,
the generator treats it as “explicitly no auth for this operation” (i.e., it suppresses global security for that operation).

### `@ApiSecurities` *(TYPE, FIELD)*
Container for multiple `@ApiSecurity`.

Fields:
- `value()` *(ApiSecurity[])*

---

## Small gotchas / conventions

- For parameters (`@ApiPathParam`, `@ApiQuery`, `@ApiHeader`, `@ApiCookie`):
    - If you set `type = SomeClass`, the generator can infer OpenAPI `type`.
    - Otherwise, you must set `schema.type()` in `@ApiParam`, or generation will fail for that parameter.
- For schemas:
    - If you use `body = SomeClass`, that class must be annotated with `@ApiSchema` or generation will fail.
    - If you use `schema = @ApiSchema(name="X", fields=[...])`, ensure `name` is set (it becomes the component key).

---

