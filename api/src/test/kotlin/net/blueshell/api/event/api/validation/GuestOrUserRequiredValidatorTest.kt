package net.blueshell.api.event.api.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.event.api.dto.EventSignUpDTO
import net.blueshell.api.factory.dto.GuestDTOFactory
import net.blueshell.api.factory.dto.event.EventSignUpDTOFactory
import net.blueshell.api.factory.dto.user.SimpleUserDTOFactory
import net.blueshell.api.event.api.validation.GuestOrUserRequiredValidator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Unit tests for GuestOrUserRequiredValidator.
 */
@SpringBootTest
class GuestOrUserRequiredValidatorTest @Autowired constructor(
    private val signUpFactory: EventSignUpDTOFactory,
    private val guestFactory: GuestDTOFactory,
    private val simpleUserFactory: SimpleUserDTOFactory
) {

    private val validator = GuestOrUserRequiredValidator()

    @Test
    fun `valid when guest present`() {
        val dto: EventSignUpDTO = signUpFactory.createWithCustomizations {
            it.eventId = 1L
            it.guest = guestFactory.createWithCustomizations { guest -> guest.name = "Guesty McGuestface" }
            it.user = null
            it.userId = null
        }

        assertTrue(validator.isValid(dto, mock<ConstraintValidatorContext>()))
    }

    @Test
    fun `valid when user present`() {
        val dto: EventSignUpDTO = signUpFactory.createWithCustomizations {
            it.eventId = 1L
            it.user = simpleUserFactory.createWithCustomizations { user -> user.id = 42L }
            it.userId = null
            it.guest = null
        }

        assertTrue(validator.isValid(dto, mock<ConstraintValidatorContext>()))
    }

    @Test
    fun `valid when userId present`() {
        val dto: EventSignUpDTO = signUpFactory.createWithCustomizations {
            it.eventId = 1L
            it.user = null
            it.guest = null
            it.userId = 99L
        }

        assertTrue(validator.isValid(dto, mock<ConstraintValidatorContext>()))
    }

    @Test
    fun `invalid when neither guest nor user provided`() {
        val dto: EventSignUpDTO = signUpFactory.createWithCustomizations {
            it.eventId = 1L
            it.guest = null
            it.user = null
            it.userId = null
        }

        val ctx = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)

        assertFalse(validator.isValid(dto, ctx))
        verify(ctx).disableDefaultConstraintViolation()
        verify(ctx).buildConstraintViolationWithTemplate("Either guest or user (or userId) must be provided.")
    }
}
