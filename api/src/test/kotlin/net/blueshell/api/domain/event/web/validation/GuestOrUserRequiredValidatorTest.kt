package net.blueshell.api.domain.event.web.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.event.web.dto.EventSignUpDTO
import net.blueshell.api.domain.event.web.dto.GuestDTO
import net.blueshell.api.domain.user.web.dto.SimpleUserDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock

class GuestOrUserRequiredValidatorTest {

    private val validator = GuestOrUserRequiredValidator()

    @Test
    fun `accepts when guest is present`() {
        val dto = EventSignUpDTO(eventId = 1, guest = GuestDTO())

        assertThat(validator.isValid(dto, mock())).isTrue()
    }

    @Test
    fun `accepts when user is present`() {
        val dto = EventSignUpDTO(eventId = 1, user = SimpleUserDTO())

        assertThat(validator.isValid(dto, mock())).isTrue()
    }

    @Test
    fun `accepts when userId is present`() {
        val dto = EventSignUpDTO(eventId = 1, userId = 42)

        assertThat(validator.isValid(dto, mock())).isTrue()
    }

    @Test
    fun `rejects when guest user and userId are all missing`() {
        val dto = EventSignUpDTO(eventId = 1, guest = null, user = null, userId = null)
        val context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)

        assertThat(validator.isValid(dto, context)).isFalse()
    }

    @Test
    fun `accepts null dto`() {
        assertThat(validator.isValid(null, mock())).isTrue()
    }
}
