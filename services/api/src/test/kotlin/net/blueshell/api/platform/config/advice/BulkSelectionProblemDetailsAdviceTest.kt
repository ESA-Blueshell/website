package net.blueshell.api.platform.config.advice

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.json.ProblemDetailJacksonMixin
import org.springframework.mock.web.MockHttpServletRequest

/**
 * The refusal shape is a published contract: the frontend branches on `code` and
 * reloads the rows named in `values`, so both survive serialisation here.
 */
class BulkSelectionProblemDetailsAdviceTest {

    private val advice = BulkSelectionProblemDetailsAdvice()

    // The same mixin Boot registers, so `errors` lands at the top level here as it does
    // on the wire rather than nested under `properties`.
    private val mapper = ObjectMapper().addMixIn(ProblemDetail::class.java, ProblemDetailJacksonMixin::class.java)

    private fun errorsOf(detail: ProblemDetail): JsonNode =
        requireNotNull(mapper.valueToTree<JsonNode>(detail).get("errors")) { "no errors property" }

    private fun refusal(vararg violations: BulkSelectionRejected.Violation) =
        advice.handleBulkSelectionRejected(
            BulkSelectionRejected("bulkContribution", violations.toList()),
            MockHttpServletRequest("POST", "/contributions/bulk/mark-paid"),
        )

    @Test
    fun `a refused selection is a conflict, not a bad request`() {
        val detail = refusal(
            BulkSelectionRejected.Violation(
                field = "userIds",
                code = BulkSelectionRejected.DELETED_USERS,
                values = listOf(42L),
                message = "1 of the selected users have been deleted.",
            ),
        )

        assertThat(detail.status).isEqualTo(HttpStatus.CONFLICT.value())
        assertThat(detail.instance?.path).isEqualTo("/contributions/bulk/mark-paid")
    }

    @Test
    fun `each reason carries its code, field and the ids it refers to`() {
        val detail = refusal(
            BulkSelectionRejected.Violation(field = "userIds", code = BulkSelectionRejected.DELETED_USERS, message = "Deleted.", values = listOf(42L)),
            BulkSelectionRejected.Violation(field = "userIds", code = BulkSelectionRejected.HONORARY_USERS, message = "Honorary.", values = listOf(57L, 58L)),
        )

        val errors = errorsOf(detail)
        assertThat(errors.map { it.get("code").asText() })
            .containsExactly(BulkSelectionRejected.DELETED_USERS, BulkSelectionRejected.HONORARY_USERS)
        assertThat(errors.map { it.get("field").asText() }).containsOnly("userIds")
        assertThat(errors.map { it.get("objectName").asText() }).containsOnly("bulkContribution")
        assertThat(errors[0].get("values").map { it.asLong() }).containsExactly(42L)
        assertThat(errors[1].get("values").map { it.asLong() }).containsExactly(57L, 58L)
    }

    @Test
    fun `the ids survive serialisation as numbers rather than a rendered string`() {
        val detail = refusal(
            BulkSelectionRejected.Violation(field = "userIds", code = BulkSelectionRejected.UNKNOWN_USERS, message = "Gone.", values = listOf(9_999_999L)),
        )

        val values = errorsOf(detail)[0].get("values")
        assertThat(values.isArray).isTrue()
        assertThat(values[0].isNumber).isTrue()
        assertThat(values[0].asLong()).isEqualTo(9_999_999L)
    }
}
