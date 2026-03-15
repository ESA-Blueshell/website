package net.blueshell.api.domain.user.application.validation

import net.blueshell.api.domain.user.application.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UniqueUsernameValidatorTest {

    private val users = mock<UserService>()
    private val validator = UniqueUsernameValidator(users)

    @Test
    fun `accepts null and blank username`() {
        assertThat(validator.isValid(null, mock())).isTrue()
        assertThat(validator.isValid("  ", mock())).isTrue()
    }

    @Test
    fun `accepts available username`() {
        whenever(users.existsByUsername("new-user")).thenReturn(false)

        assertThat(validator.isValid("new-user", mock())).isTrue()
    }

    @Test
    fun `rejects taken username`() {
        whenever(users.existsByUsername("taken-user")).thenReturn(true)

        assertThat(validator.isValid("taken-user", mock())).isFalse()
    }
}
