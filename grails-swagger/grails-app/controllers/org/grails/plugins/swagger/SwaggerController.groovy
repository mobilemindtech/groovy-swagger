package org.grails.plugins.swagger

import grails.converters.JSON
import grails.core.GrailsApplication

class SwaggerController {
    GrailsApplication grailsApplication

    def openApiService

    static defaultAction = "index"

    // Novo endpoint para renderizar o HTML do UI
    def index() {
        try {
            loadConfigs()
            render(text: mkHtml(), contentType: "text/html", encoding: "UTF-8")
        }catch (Exception err){
            render(text: "<h2>$err.message</h2>", contentType: "text/html", encoding: "UTF-8")
        }
    }

    def api() {
        try{
            def (configClass, packageName) = loadConfigs()
            def data = openApiService.makeJSON(configClass: configClass, packageName: packageName)
            header("Access-Control-Allow-Origin", request.getHeader('Origin'))
            render(status: 200, contentType: "application/json", text: data)
        }catch (Exception err){
            log.error err.message, err
            render(status: 500, contentType: "application/json", text: [message: err.message] as JSON)
        }
    }

    def internal() {
        header("Access-Control-Allow-Origin", request.getHeader('Origin'))
        render(status: 200, contentType: "application/json", text: Json.mapper().writeValueAsString(SwaggerApi.apis))
    }

    def assets(){
        String file = request.requestURL.toString().split("/").last()

        if(!file.endsWith('.css') && !file.endsWith('.js')) {
            render status: 404
            return
        }

        def ctype = file.endsWith('.css') ? 'text/css' : 'application/javascript'
        def is = this.class.classLoader.getResourceAsStream("swagger-ui/${file}")

        if (is) {
            render file: is.bytes, contentType: ctype
        } else {
            render status: 404
        }
    }

    private String mkHtml(){
        String jsonUrl = createLink(action: 'api', absolute: true)

        // HTML básico que carrega o Swagger UI dos WebJars
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>Swagger UI</title>
            <link rel="stylesheet" type="text/css" href="/swagger/assets/swagger-ui.css" >
            <style>
              html { box-sizing: border-box; overflow-y: scroll; }
              *, *:before, *:after { box-sizing: inherit; }
              body { margin:0; background: #fafafa; }
            </style>
        </head>
        <body>
            <div id="swagger-ui"></div>
            <script src="/swagger/assets/swagger-ui-bundle.js"> </script>
            <script src="/swagger/assets/swagger-ui-standalone-preset.js"> </script>            
            <script>
            window.onload = function() {
              const ui = SwaggerUIBundle({
                url: "${jsonUrl}",
                dom_id: '#swagger-ui',
                deepLinking: true,
                presets: [
                  SwaggerUIBundle.presets.apis,
                  SwaggerUIStandalonePreset
                ],
                plugins: [
                  SwaggerUIBundle.plugins.DownloadUrl
                ],
                layout: "StandaloneLayout"
              })
              window.ui = ui
            }
          </script>
        </body>
        </html>
        """
    }

    private Tuple2<Class, String> loadConfigs(){
        def configClassStr = grailsApplication.config.getProperty("grails.plugins.swagger.config")
        def configPackage = grailsApplication.config.getProperty("grails.plugins.swagger.package")

        if(!configClassStr){
            throw new RuntimeException("Configuration 'grails.plugins.swagger.config' not found")
        }

        if(!configPackage){
            throw new RuntimeException("Configuration 'grails.plugins.swagger.package' not found")
        }

        try {
            return Tuple.tuple(Class.forName(configClassStr), configPackage)
        }catch (Exception err){
            log.error err.message, err
            throw new RuntimeException("Error on load config class $configClassStr: $err.message", err)
        }
    }
}
