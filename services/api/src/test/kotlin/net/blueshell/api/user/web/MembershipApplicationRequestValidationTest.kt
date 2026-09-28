package net.blueshell.api.user.web

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MembershipApplicationRequestValidationTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    private fun refusedFields(body: MembershipApplicationRequest) = validator.validate(body).map { it.propertyPath.toString() }

    @Test
    fun `refuses conditions that were not accepted`() {
        assertThat(refusedFields(MembershipApplicationRequest(conditionsAccepted = false)))
            .containsExactly("conditionsAccepted")
    }

    @Test
    fun `refuses an omitted acceptance as it refuses a false one`() {
        assertThat(refusedFields(MembershipApplicationRequest())).containsExactly("conditionsAccepted")
    }

    @Test
    fun `passes once the conditions are accepted`() {
        assertThat(refusedFields(MembershipApplicationRequest(conditionsAccepted = true))).isEmpty()
    }
}
