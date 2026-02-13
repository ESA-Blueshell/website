package net.blueshell.api.platform.config

import com.fasterxml.jackson.databind.JsonNode
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

/**
 * Integration test asserting OpenAPI global error components and responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiErrorConfigIT : UserTestSupport() {

    @Test
    fun openapi_contains_global_error_responses_and_error_schemas() {
        val res = mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()

        val root = mapper.readTree(res.response.contentAsString)

        val hasApiErrorSchema =
            root.at("/components/schemas/ApiError").isObject ||
                    root.at("/components/schemas/ApiErrorDTO").isObject

        val hasFieldErrorSchema =
            root.at("/components/schemas/FieldValidationError").isObject ||
                    root.at("/components/schemas/FieldValidationErrorDTO").isObject

        Assertions.assertTrue(hasApiErrorSchema, "Expected ApiError* schema present in OpenAPI components.")
        Assertions.assertTrue(
            hasFieldErrorSchema,
            "Expected FieldValidationError* schema present in OpenAPI components."
        )

        val putResponses: JsonNode = root.at("/paths/~1events~1{eventId}~1signups/put/responses")
        Assertions.assertTrue(putResponses.has("400"), "PUT /events/{eventId}/signups should document 400")
        Assertions.assertTrue(putResponses.has("401"), "PUT /events/{eventId}/signups should document 401")
        Assertions.assertTrue(putResponses.has("403"), "PUT /events/{eventId}/signups should document 403")
        Assertions.assertTrue(putResponses.has("404"), "PUT /events/{eventId}/signups should document 404")
        Assertions.assertTrue(putResponses.has("500"), "PUT /events/{eventId}/signups should document 500")
    }
}