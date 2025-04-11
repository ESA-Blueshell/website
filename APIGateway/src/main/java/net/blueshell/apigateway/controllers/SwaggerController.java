package net.blueshell.apigateway.controllers;

import org.springframework.web.bind.annotation.GetMapping;

public class SwaggerController {
    protected static final String SWAGGER_SERVICE_URL = "/v3/api-docs";

    @GetMapping("/v3/api-docs")
    public Object apiDocs() {
        return sendSwaggerRequestToService();
    }

    protected Object sendSwaggerRequestToService() {
        return null;
    }
}
