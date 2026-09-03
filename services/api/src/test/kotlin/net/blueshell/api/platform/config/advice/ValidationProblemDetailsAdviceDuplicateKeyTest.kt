package net.blueshell.api.platform.config.advice

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.json.ProblemDetailJacksonMixin
import org.springframework.mock.web.MockHttpServletRequest

/**
 * A uniqueness rule the database enforced after the pre-insert check let it
 * through. Answering 500 sent an applicant who had typed a taken username off to
 * report a bug and retry into the same wall, so the shape here has to match what
 * the pre-insert check produces: same field, same wording.
 */
class ValidationProblemDetailsAdviceDuplicateKeyTest {

    private val advice = ValidationProblemDetailsAdvice()

    private val mapper = ObjectMapper().addMixIn(ProblemDetail::class.java, ProblemDetailJacksonMixin::class.java)

    private fun errorsOf(detail: ProblemDetail): JsonNode? =
        mapper.valueToTree<JsonNode>(detail).get("errors")

    /** The driver names the constraint well down the cause chain, not on top. */
    private fun violation(constraint: String) = DataIntegrityViolationException(
        "could not execute statement",
        java.sql.SQLIntegrityConstraintViolationException(
            "Duplicate entry 'lena' for key '$constraint'"
        ),
    )

    private fun handle(constraint: String) =
        advice.handleDataIntegrityViolation(violation(constraint), MockHttpServletRequest("POST", "/signup"))

    @Test
    fun `a taken username is a field the applicant can fix`() {
        val detail = handle("uk_users_username_deleted_at")

        assertThat(detail.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
        val errors = requireNotNull(errorsOf(detail)) { "no errors property" }
        assertThat(errors[0]["field"].asText()).isEqualTo("username")
        assertThat(errors[0]["message"].asText()).isEqualTo("Username is taken.")
        assertThat(errors[0]["code"].asText()).isEqualTo("Unique")
    }

    @Test
    fun `every uniqueness rule a person can retype is named`() {
        val expected = mapOf(
            "uk_users_email_deleted_at" to ("email" to "Email is taken."),
            "uk_users_discord_deleted_at" to ("discord" to "Discord is taken."),
            "uk_users_phone_number_deleted_at" to ("phoneNumber" to "Phone number is taken."),
        )

        expected.forEach { (constraint, expectation) ->
            val errors = requireNotNull(errorsOf(handle(constraint))) { constraint }
            assertThat(errors[0]["field"].asText()).describedAs(constraint).isEqualTo(expectation.first)
            assertThat(errors[0]["message"].asText()).describedAs(constraint).isEqualTo(expectation.second)
        }
    }

    /**
     * Retyping cannot free an address another account holds, so there is no field
     * to hang it on and the client gets a conflict rather than a phantom error.
     */
    @Test
    fun `a conflict nobody can retype stays a bare conflict`() {
        val detail = handle("uk_users_address_id_deleted_at")

        assertThat(detail.status).isEqualTo(HttpStatus.CONFLICT.value())
        assertThat(errorsOf(detail)).isNull()
    }

    @Test
    fun `an unrecognised integrity failure is still not a server error`() {
        val detail = handle("fk_something_entirely_different")

        assertThat(detail.status).isEqualTo(HttpStatus.CONFLICT.value())
        assertThat(errorsOf(detail)).isNull()
    }
}
