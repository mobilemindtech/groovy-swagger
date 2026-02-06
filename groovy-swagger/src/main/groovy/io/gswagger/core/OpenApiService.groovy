package io.gswagger.core

import com.google.common.reflect.ClassPath
import groovy.json.JsonBuilder
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j
import io.gswagger.annotations.ApiConfig
import io.gswagger.annotations.ApiContent
import io.gswagger.annotations.ApiCookie
import io.gswagger.annotations.ApiCookies
import io.gswagger.annotations.ApiHeader
import io.gswagger.annotations.ApiHeaders
import io.gswagger.annotations.ApiOperation
import io.gswagger.annotations.ApiPathParam
import io.gswagger.annotations.ApiParam
import io.gswagger.annotations.ApiPathParams
import io.gswagger.annotations.ApiQueries
import io.gswagger.annotations.ApiQuery
import io.gswagger.annotations.ApiRequestBody
import io.gswagger.annotations.ApiResource
import io.gswagger.annotations.ApiResponse
import io.gswagger.annotations.ApiResponses
import io.gswagger.annotations.ApiSchema
import io.gswagger.annotations.ApiSchemaField
import io.gswagger.annotations.ApiSchemas
import io.gswagger.annotations.ApiSecurities
import io.gswagger.annotations.ApiSecurity
import io.gswagger.annotations.ApiSecurityScheme
import io.gswagger.annotations.ApiServers
import io.gswagger.annotations.SecurityType

import java.lang.annotation.Annotation
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.stream.Collectors

@Slf4j
class OpenApiService {

    @TupleConstructor
    static class Config {
        Class configClass
        String packageName
        List<Class> classes
        boolean prettyJson
    }

    private Map components = [schemas: [:], securitySchemes: [:]]

    String makeJSON(Map config) {
        makeJSON(new Config(config))
    }
    
    String makeJSON(Config config) {

        assert config.configClass : "configClass is required"
        assert config.packageName || config.classes : "classes or packageName is required"

        def controllers = config.classes ?: []

        if (config.packageName) {
            controllers += findAllClassesInPackage(config.packageName)
        }

        def apiConfig = config.configClass.getAnnotation(ApiConfig) as ApiConfig

        assert apiConfig: "ApiConfig annotation is required in config class"

        def servers = (config.configClass.getAnnotation(ApiServers) as ApiServers)?.value()?.toList() ?: []
        def secSchemes = apiConfig.securitySchemes()?.toList() ?: []

        processSecuritySchemes(secSchemes)

        def openApi = [
            openapi   : apiConfig.openapi() ?: "3.0.1",
            info      : [title: apiConfig.title(), description: apiConfig.description(), version: apiConfig.version()],
            servers   : (servers + apiConfig.servers().toList())
                        .collect {
                            [url: it.url(), description: it.description()]
                        },
            paths: [:],
            components: components
        ]

        def schemas = (config.configClass.getAnnotation(ApiSchemas) as ApiSchemas)?.value()?.toList() ?: []
        schemas += apiConfig.schemas().toList()

        schemas.each { schema ->
            registerSchema(schema)
        }

        controllers.each { processController(apiConfig, it, openApi.paths, secSchemes) }


        def builder = new JsonBuilder(openApi)
        config.prettyJson ? builder.toPrettyString() : builder.toString()
    }

    private void processSecuritySchemes(List<ApiSecurityScheme> secSchemes) {
        secSchemes.each { ann ->
            def scheme = [description: ann.description()]

            switch (ann.type()) {
                case SecurityType.BEARER:
                    scheme += [type: "http", scheme: "bearer", bearerFormat: "JWT"]
                    break
                case SecurityType.BASIC:
                    scheme += [type: "http", scheme: "basic"]
                    break
                case SecurityType.API_KEY:
                    scheme += [type: "apiKey", in: "header", name: ann.headerName()]
                    break
                case SecurityType.NONE:
                    return
            }
            components.securitySchemes[ann.name()] = scheme
        }
    }

    private <T extends Annotation> List<T> mergeAnnotations(Class type, Class<T> ann, Class<? extends  Annotation> annList) {
        def items = type.getAnnotationsByType(ann).toList()
        items.addAll(type.getAnnotation(annList)?.value() ?: [])
        items
    }

    private <T extends Annotation> List<T> mergeAnnotations(Field field, Class<T> ann, Class<? extends  Annotation> annList) {
        def items = field.getAnnotationsByType(ann).toList()
        items.addAll(field.getAnnotation(annList)?.value() ?: [])
        items
    }

    private void processController(ApiConfig config, Class ctrl, Map paths, List<ApiSecurityScheme> secSchemes) {

        if(!ctrl.isAnnotationPresent(ApiResource))
            return

        def resource = ctrl.getAnnotation(ApiResource) as ApiResource

        def path = resource.path()

        def noAuthSchemeName = secSchemes.find { it.type() == SecurityType.NONE }?.name() ?: "-"

        def globalSecurities = mergeAnnotations(ctrl, ApiSecurity, ApiSecurities)
                .findAll { it.value() != noAuthSchemeName }

        def globalHeaders = mergeAnnotations(ctrl, ApiHeader, ApiHeaders)
        def globalCookies = mergeAnnotations(ctrl, ApiCookie, ApiCookies)

        def globalContents = resource.contents() ?: config.contents()

        ctrl.declaredFields.findAll { it.isAnnotationPresent(ApiOperation) }.each { field ->
            def action = field.getAnnotation(ApiOperation) as ApiOperation

            def securities = mergeAnnotations(field, ApiSecurity, ApiSecurities)
            def headers = mergeAnnotations(field, ApiHeader, ApiHeaders)
            def params = mergeAnnotations(field, ApiPathParam, ApiPathParams)
            def queries = mergeAnnotations(field, ApiQuery, ApiQueries)
            def cookies = mergeAnnotations(field, ApiCookie, ApiCookies)

            def hasNoAuthScheme = securities.any { it.value() == noAuthSchemeName }


            def fullPath = (path + action.path()).replaceAll(/\/+/, "/")

            def operation = [
                description: action.description(),
                summary: action.summary(),
                deprecated: action.deprecated(),
                tags: [ctrl.simpleName]
            ]

            if(!hasNoAuthScheme) {
                def effectiveSecurity = securities.size() > 0 ? securities : globalSecurities
                if (effectiveSecurity) {
                    operation.security = effectiveSecurity.collect { sec ->
                        [(sec.value()): sec.scopes()]
                    }
                }
            }


            def requestBody = field.getAnnotation(ApiRequestBody) as ApiRequestBody

            // 3. Request Body (Recursivo)
            if (requestBody) {

                def contents = (requestBody.contents() ?: globalContents).toList()
                operation.requestBody = [description: requestBody.description()]

                if(requestBody.body() != Void || requestBody.schema()) {
                    operation.requestBody.content = contents.inject([:]) {
                        acc, media ->
                            def schema = requestBody.body() == Void ?
                                    resolveSchema(requestBody.schema().first(), requestBody.isList()) : resolveSchema(requestBody.body(), requestBody.isList())
                            acc + [(media.contentType()): [schema: schema]]
                    }
                }
            }

            def respContents = field.getAnnotation(ApiResponses)?.contents()?.toList() ?: []
            def responses = mergeAnnotations(field, ApiResponse, ApiResponses)


            if(responses) {
                operation.responses = [:]
                List<ApiContent> contents = respContents ?: globalContents.toList()

                responses.each { resp ->

                    contents = resp.contents().toList() ?: contents

                    def resDef = [description: resp.description() ?: "Status ${resp.statusCode()}"]
                    if (resp.body() != Void || resp.schema()) {
                        resDef.content = contents.inject([:]) {
                            acc, media ->
                                def schema = resp.body() == Void ?
                                        resolveSchema(resp.schema().first(), resp.isList()) : resolveSchema(resp.body(), resp.isList())
                                acc + [(media.contentType()): [schema: schema]]
                        }
                    }
                    operation.responses[resp.statusCode().toString()] = resDef
                }
            }

            def allHeaders  = globalHeaders + headers
            if(allHeaders) {
                operation.parameters = operation.parameters ?: []
                allHeaders.each { p ->
                    operation.parameters << [
                            name       : p.name(),
                            in         : "header",
                            required   : p.required(),
                            description: p.description(),
                            example    : p.example(),
                            schema     : getParameterSchema(p.type(), p.schema().find(), p.name(), fullPath)
                    ]
                }
            }

            def allCookies = (globalCookies + cookies)
            if(allCookies) {
                operation.parameters = operation.parameters ?: []
                allCookies.each { p ->
                    operation.parameters << [
                            name       : p.name(),
                            in         : "cookie",
                            required   : p.required(),
                            description: p.description(),
                            example    : p.example(),
                            schema     : getParameterSchema(p.type(), p.schema(), p.name(), fullPath)
                    ]
                }
            }

            if(params) {
                operation.parameters = operation.parameters ?: []
                params.each { p ->
                    operation.parameters << [
                            name       : p.name(),
                            in         : "path",
                            required   : p.required(),
                            description: p.description(),
                            example    : p.example(),
                            schema     : getParameterSchema(p.type(), p.schema().find(), p.name(), fullPath)
                    ]
                }
            }

            if(queries) {
                operation.parameters = operation.parameters ?: []
                queries.each { p ->
                    operation.parameters << [
                            name       : p.name(),
                            in         : "query",
                            required   : p.required(),
                            description: p.description(),
                            example    : p.example(),
                            schema     : getParameterSchema(p.type(), p.schema().find(), p.name(), fullPath)
                    ]
                }
            }

            operation.parameters.each {
                it.removeAll { k, v  -> v == false || v == "" }
            }

            paths.computeIfAbsent(fullPath, { [:] })[action.method().name().toLowerCase()] = operation
        }

    }

    private Map getParameterSchema(Class type, ApiParam schema, String name, String path){

        if(type != Void) {
            if(type.isEnum()){
                return [type: 'string', 'enum': type.enumConstants]
            }

            return [type: getJsonType(type)]
        }

        if(schema.type() == Void)
            throw new Exception("Can't get parameter type for $name in path $path")

        def data = [
            title: schema.title(),
            description: schema.description(),
            type: getJsonType(schema.type()),
            maximum: schema.maximum(),
            minimum: schema.minimum(),
            maxLength: schema.maxLength(),
            minLength: schema.minLength(),
            pattern: schema.pattern(),
            maxItems: schema.maxItems(),
            minItems: schema.minItems(),
            uniqueItems: schema.uniqueItems(),
            required: schema.required(),
            'enum': schema.options(),
            format: schema.format(),
            'default': schema.defaultValue(),
        ]
        data.removeAll {
            it.value == -1 || it.value == "" || it.value == false || (it.key == 'enum' && it.value.length == 0)
        }

        if(data.type == 'array')
            data.items = [type:  getJsonType(schema.typeItem())]

        data
    }

    private Map resolveSchema(Class clazz, boolean isList) {
        def schema = clazz.getAnnotation(ApiSchema) as ApiSchema

        if(!schema) {
            throw new RuntimeException("Annotation @ApiSchema not found to type ${clazz.simpleName}")
        }

        def name = schema.name() ?: clazz.simpleName

        if (!components.schemas.containsKey(name)) {
            Map props = [:]
            clazz.declaredFields
                    .grep {  !it.synthetic }
                    .findAll { it.isAnnotationPresent(ApiSchemaField) }
                    .each { f ->

                        def ann = f.getAnnotation(ApiSchemaField)
                        def type = f.type
                        def pname = ann.name() ?: f.name
                        def pIsList = Collection.isAssignableFrom(f.type)
                        def isEnum = type.isEnum()

                        if(isEnum){
                            props[pname] = [
                                    type       : "string",
                                    example    : ann.example(),
                                    description: ann.description(),
                                    'enum': ann.options() ?: type.enumConstants
                            ]
                        } else {

                            if (type.isAnnotationPresent(ApiSchema)) {
                                def ref = resolveSchema(type, false)
                                if (pIsList)
                                    props[pname] = [type: "array", items: ref]
                                else
                                    props[pname] = ref
                            } else {

                                props[pname] = [
                                        type       : getJsonType(type),
                                        example    : ann.example(),
                                        description: ann.description()
                                ]

                                if(ann.options()){
                                    props[pname]= ['enum': ann.options()]
                                }

                                if (pIsList) {
                                    def genericType = getFieldGenericType(f)
                                    if (genericType) {
                                        def ref = getJsonType(genericType)
                                        props[pname].items = ref instanceof Map ? ref : [type: ref]
                                    } else {
                                        props[pname].items = [type: "object"]
                                    }
                                }
                            }
                        }

                    }
            components.schemas[name] = [
                    type: "object",
                    description: schema.description(),
                    properties: props
            ]
        }

        def ref = [ '$ref': "#/components/schemas/$name" ]
        isList ? [type: 'array', items: ref] : ref
    }

    private void registerSchema(ApiSchema schema) {
        resolveSchema(schema, false)
    }

    private Map resolveSchema(ApiSchema schema, boolean isList = false) {
        def mkRef = { name, list ->
            def ref = [ '$ref': "#/components/schemas/$name" ]
            list ? [type: "array", items: ref] : ref
        }

        def name = schema.name()

        if (!components.schemas.containsKey(name)) {
            Map props = [:]
            schema.fields().each {f ->

                def pname = f.name()

                if(f.type() == Void && !f.schema())
                    throw new Exception("Type not found to schema $name")

                if (f.type() == Void) {
                    props[pname] = mkRef(f.schema().first().name(), f.isList())
                } else {

                    if(f.type().isEnum()){
                        props[pname] = [
                                type       : 'string',
                                example    : f.example(),
                                description: f.description(),
                                'enum': f.options() ?: f.type().enumConstants]
                    } else {
                        def typ = getJsonType(f.type(), true)
                        props[pname] = [
                                type       : typ,
                                example    : f.example(),
                                description: f.description()]

                        if(f.options()){
                            props[pname].'enum' = f.options()
                        }

                    }
                }
            }

            components.schemas[name] = [type: "object", properties: props]
        }

        mkRef(name, isList)
    }

    private Class getFieldGenericType(Field field){
        def genericType = field.getGenericType()
        if(genericType instanceof ParameterizedType){
            Type[] actualTypeArguments = ((ParameterizedType) genericType).getActualTypeArguments()
            return (Class<?>) actualTypeArguments[0]
        }
        return null
    }

    private Object getJsonType(Class type, boolean onlySimpleType = false) {
        switch (type){
            case [Integer, int, Long, long, Short, short]:
                return "integer"
            case [Boolean, boolean]:
                return "boolean"
            case [Double, double, Float, float, BigDecimal]:
                return "number"
            case String:
                return "string"
            default:

                if(Collection.isAssignableFrom(type)) return "array"

                if(onlySimpleType) return "object"

                resolveSchema(type, false)
        }
    }

    private Set<Class> findAllClassesInPackage(String packageName) {
        def classLoader = Thread.currentThread().contextClassLoader
        def classPath = ClassPath.from(classLoader)

        classPath.getAllClasses()
                .stream()
                .filter { clazz -> clazz.getPackageName() == packageName }
                .map { clazzInfo -> clazzInfo.load() }
                .filter { cls -> cls.isAnnotationPresent(ApiResource) }
                .collect(Collectors.toSet())
    }
}
