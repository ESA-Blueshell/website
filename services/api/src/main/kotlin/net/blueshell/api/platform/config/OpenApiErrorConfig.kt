package net.blueshell.api.platform.config

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import net.blueshell.api.platform.web.dto.error.ApiErrorDTO
import net.blueshell.api.platform.web.dto.error.FieldValidationErrorDTO
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import java.util.function.Consumer

@Configuration
class OpenApiErrorConfig {
    @Bean
    fun globalErrorResponsesCustomizer(): OpenApiCustomizer {
        return OpenApiCustomizer { openApi: OpenAPI? ->
            ensureSchemas(openApi!!)
            val unauthorized = ApiResponse()
                .description("Unauthorized")
                .content(
                    Content().addMediaType(
                        MediaType.APPLICATION_JSON_VALUE,
                        io.swagger.v3.oas.models.media.MediaType()
                            .schema(refSchema("ApiError"))
                            .example(unauthorizedExample())
                    )
                )

            val forbidden = ApiResponse()
                .description("Forbidden (access denied)")
                .content(
                    Content().addMediaType(
                        MediaType.APPLICATION_JSON_VALUE,
                        io.swagger.v3.oas.models.media.MediaType()
                            .schema(refSchema("ApiError"))
                            .example(forbiddenExample())
                    )
                )

            val validationError = ApiResponse()
                .description("Validation error")
                .content(
                    Content().addMediaType(
                        MediaType.APPLICATION_JSON_VALUE,
                        io.swagger.v3.oas.models.media.MediaType()
                            .schema(refSchema("ApiError"))
                            .example(validationExample())
                    )
                )

            val notFoundError = ApiResponse()
                .description("Not Found")
                .content(
                    Content().addMediaType(
                        MediaType.APPLICATION_JSON_VALUE,
                        io.swagger.v3.oas.models.media.MediaType()
                            .schema(refSchema("ApiError"))
                            .example(notFoundExample())
                    )
                )

            val serverError = ApiResponse()
                .description("Server error")
                .content(
                    Content().addMediaType(
                        MediaType.APPLICATION_JSON_VALUE,
                        io.swagger.v3.oas.models.media.MediaType()
                            .schema(refSchema("ApiError"))
                            .example(serverErrorExample())
                    )
                )
            openApi.paths.values.forEach(Consumer { pathItem: PathItem? ->
                pathItem!!.readOperations().forEach(
                    Consumer { operation: Operation? ->
                        val responses = operation!!.responses
                        // Security failures from @PreAuthorize / Spring Security
                        if (!responses.containsKey("401")) {
                            responses.addApiResponse("401", unauthorized)
                        }
                        if (!responses.containsKey("403")) {
                            responses.addApiResponse("403", forbidden)
                        }
                        // Your existing global errors
                        if (!responses.containsKey("400")) {
                            responses.addApiResponse("400", validationError)
                        }
                        if (!responses.containsKey("404")) {
                            responses.addApiResponse("404", notFoundError)
                        }
                        if (!responses.containsKey("500")) {
                            responses.addApiResponse("500", serverError)
                        }
                    })
            }
            )
        }
    }

    private fun ensureSchemas(openApi: OpenAPI) {
        if (openApi.components == null) {
            openApi.components = Components()
        }
        val apiErrorSchemas = ModelConverters.getInstance().read(ApiErrorDTO::class.java)
        val fieldErrorSchemas = ModelConverters.getInstance().read(FieldValidationErrorDTO::class.java)

        openApi.components.schemas.putAll(apiErrorSchemas)
        openApi.components.schemas.putAll(fieldErrorSchemas)
    }

    private fun refSchema(name: String?): Schema<*>? {
        return Schema<Any?>().`$ref`("#/components/schemas/$name")
    }

    // ───────────────────── Examples ─────────────────────
    private fun unauthorizedExample(): Any {
        // Typical when not authenticated and hitting a @PreAuthorize-protected endpoint.
        return """
                {
                  "type": "about:blank",
                  "title": "Unauthorized",
                  "status": 401,
                  "detail": "Full authentication is required to access this resource",
                  "instance": "/api/users",
                  "traceId": "401401401401"
                }
                
                """.trimIndent()
    }

    private fun forbiddenExample(): Any {
        // Typical when authenticated but fails @PreAuthorize expression (AccessDeniedException).
        return """
                {
                  "type": "about:blank",
                  "title": "Forbidden",
                  "status": 403,
                  "detail": "Access is denied",
                  "instance": "/api/users",
                  "traceId": "403403403403"
                }
                
                """.trimIndent()
    }

    private fun validationExample(): Any {
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
                      "message": "must be a well-formed email address",
                      "code": "Email"
                    },
                    {
                      "objectName": "createUserRequest",
                      "field": "age",
                      "message": "must be greater than or equal to 0",
                      "code": "Min"
                    }
                  ],
                  "traceId": "a8c0c4e5f1c24a7e"
                }
                
                """.trimIndent()
    }

    private fun notFoundExample(): Any {
        return """
                {
                  "type": "about:blank",
                  "title": "Not Found",
                  "status": 404,
                  "detail": "User not found with id: 42",
                  "instance": "/api/users/42",
                  "traceId": "cdef1234abcd5678"
                }
                
                """.trimIndent()
    }

    private fun serverErrorExample(): Any {
        return """
                {
                  "type": "about:blank",
                  "title": "Internal Server Error",
                  "status": 500,
                  "detail": "An unexpected error occurred.",
                  "instance": "/api/users",
                  "traceId": "ab12cd34ef56"
                }
                
                """.trimIndent()
    }
}
