package net.blueshell.api.user.web

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MembershipTypeRequestValidationTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `a type names its kind and a day that is not in the future`() {
        assertThat(validator.validate(MembershipTypeRequest()).map { it.message })
            .containsExactlyInAnyOrder("Pick a membership type.", "Fill in the day this type starts.")
        assertThat(validator.validate(MembershipTypeRequest(since = LocalDate.now().plusDays(1))).map { it.message })
            .contains("A membership type cannot start after today.")
    }
}
