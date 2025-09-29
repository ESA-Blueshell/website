package net.blueshell.api.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import net.blueshell.api.dto.error.ApiErrorDTO;
import net.blueshell.api.dto.error.FieldValidationErrorDTO;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class OpenApiErrorConfig {

    @Bean
    public OpenApiCustomizer globalErrorResponsesCustomizer() {
        return openApi -> {
            ensureSchemas(openApi);

            ApiResponse validationError = new ApiResponse()
                    .description("Validation error")
                    .content(new Content().addMediaType(
                            org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                            new MediaType()
                                    .schema(refSchema("ApiError"))
                                    .example(validationExample())
                    ));

            ApiResponse notFoundError = new ApiResponse()
                    .description("Not Found")
                    .content(new Content().addMediaType(
                            org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                            new MediaType()
                                    .schema(refSchema("ApiError"))
                                    .example(notFoundExample())
                    ));

            ApiResponse serverError = new ApiResponse()
                    .description("Server error")
                    .content(new Content().addMediaType(
                            org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                            new MediaType()
                                    .schema(refSchema("ApiError"))
                                    .example(serverErrorExample())
                    ));

            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        ApiResponses responses = operation.getResponses();
                        if (!responses.containsKey("400")) {
                            responses.addApiResponse("400", validationError);
                        }
                        if (!responses.containsKey("404")) {
                            responses.addApiResponse("404", notFoundError);
                        }
                        if (!responses.containsKey("500")) {
                            responses.addApiResponse("500", serverError);
                        }
                    })
            );
        };
    }

    private void ensureSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        Map<String, Schema> apiErrorSchemas = ModelConverters.getInstance().read(ApiErrorDTO.class);
        Map<String, Schema> fieldErrorSchemas = ModelConverters.getInstance().read(FieldValidationErrorDTO.class);

        openApi.getComponents().getSchemas().putAll(apiErrorSchemas);
        openApi.getComponents().getSchemas().putAll(fieldErrorSchemas);
    }

    private Schema<?> refSchema(String name) {
        return new Schema<>().$ref("#/components/schemas/" + name);
    }

    private Object validationExample() {
        return """
        {
          "type": "about:blank",
          "title": "Bad Request",
          "status": 400,
          "detail": "Validation failed for request.",
          "instance": "/api/users",
          "errors": [
            {
              "objectName": "createUserRequest",
              "field": "email",
              "rejectedValue": "not-an-email",
              "message": "must be a well-formed email address",
              "code": "Email"
            },
            {
              "objectName": "createUserRequest",
              "field": "age",
              "rejectedValue": -1,
              "message": "must be greater than or equal to 0",
              "code": "Min"
            }
          ],
          "traceId": "a8c0c4e5f1c24a7e"
        }
        """;
    }

    private Object notFoundExample() {
        return """
        {
          "type": "about:blank",
          "title": "Not Found",
          "status": 404,
          "detail": "User not found with id: 42",
          "instance": "/api/users/42",
          "traceId": "cdef1234abcd5678"
        }
        """;
    }

    private Object serverErrorExample() {
        return """
        {
          "type": "about:blank",
          "title": "Internal Server Error",
          "status": 500,
          "detail": "An unexpected error occurred.",
          "instance": "/api/users",
          "traceId": "ab12cd34ef56"
        }
        """;
    }
}
