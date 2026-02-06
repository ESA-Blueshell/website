package net.blueshell.api.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration test asserting OpenAPI global error components and responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiErrorConfigIT : UserTestSupport() {

    @Autowired
    private lateinit var mapper: ObjectMapper

    @Test
    fun openapi_contains_global_error_responses_and_error_schemas() {
        val res = mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andReturn()

        val root = mapper.readTree(res.response.contentAsString)

        val hasApiErrorSchema =
            root.at("/components/schemas/ApiError").isObject ||
                root.at("/components/schemas/ApiErrorDTO").isObject

        val hasFieldErrorSchema =
            root.at("/components/schemas/FieldValidationError").isObject ||
                root.at("/components/schemas/FieldValidationErrorDTO").isObject

        assertTrue(hasApiErrorSchema, "Expected ApiError* schema present in OpenAPI components.")
        assertTrue(hasFieldErrorSchema, "Expected FieldValidationError* schema present in OpenAPI components.")

        val putResponses: JsonNode = root.at("/paths/~1events~1{eventId}~1signups/put/responses")
        assertTrue(putResponses.has("400"), "PUT /events/{eventId}/signups should document 400")
        assertTrue(putResponses.has("401"), "PUT /events/{eventId}/signups should document 401")
        assertTrue(putResponses.has("403"), "PUT /events/{eventId}/signups should document 403")
        assertTrue(putResponses.has("404"), "PUT /events/{eventId}/signups should document 404")
        assertTrue(putResponses.has("500"), "PUT /events/{eventId}/signups should document 500")
    }
}
