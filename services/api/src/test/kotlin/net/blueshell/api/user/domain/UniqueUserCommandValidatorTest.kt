package net.blueshell.api.user.domain

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UniqueUserCommandValidatorTest {
    private val users = mock<UserService>()
    private val validator = UniqueUserCommandValidator(users)

    @Test
    fun `accepts null candidate`() {
        assertThat(validator.isValid(null, mock())).isTrue()
    }

    @Test
    fun `accepts when all provided fields are unique on create`() {
        whenever(users.existsByUsername("new-user")).thenReturn(false)
        whenever(users.existsByEmail("new@example.com")).thenReturn(false)
        whenever(users.existsByDiscordId("1144058844004233369")).thenReturn(false)
        whenever(users.existsByPhoneNumber("+31612345678")).thenReturn(false)

        val candidate =
            candidate(
                subjectId = null,
                username = "new-user",
                email = "new@example.com",
                discordId = "1144058844004233369",
                phoneNumber = "+31612345678",
            )

        assertThat(validator.isValid(candidate, mock())).isTrue()
    }

    @Test
    fun `rejects create candidate when any unique field is taken`() {
        whenever(users.existsByUsername("taken-user")).thenReturn(true)
        whenever(users.existsByEmail("taken@example.com")).thenReturn(true)
        whenever(users.existsByDiscordId("1144058844004233370")).thenReturn(true)
        whenever(users.existsByPhoneNumber("+31687654321")).thenReturn(true)

        val context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val candidate =
            candidate(
                subjectId = null,
                username = "taken-user",
                email = "taken@example.com",
                discordId = "1144058844004233370",
                phoneNumber = "+31687654321",
            )

        assertThat(validator.isValid(candidate, context)).isFalse()
    }

    @Test
    fun `uses id-not checks for updates`() {
        whenever(users.existsByUsernameAndIdNot("same-user", 42)).thenReturn(true)
        whenever(users.existsByEmailAndIdNot("same@example.com", 42)).thenReturn(false)
        whenever(users.existsByDiscordIdAndIdNot("1144058844004233371", 42)).thenReturn(false)
        whenever(users.existsByPhoneNumberAndIdNot("+31611112222", 42)).thenReturn(false)

        val context = mock<ConstraintValidatorContext>(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val candidate =
            candidate(
                subjectId = 42,
                username = "same-user",
                email = "same@example.com",
                discordId = "1144058844004233371",
                phoneNumber = "+31611112222",
            )

        assertThat(validator.isValid(candidate, context)).isFalse()
    }

    private fun candidate(
        subjectId: Long?,
        username: String?,
        email: String?,
        discordId: String?,
        phoneNumber: String?,
    ): UserUniquenessCandidate =
        object : UserUniquenessCandidate {
            override val subjectId = subjectId
            override val username = username
            override val email = email
            override val discordId = discordId
            override val phoneNumber = phoneNumber
        }
}
