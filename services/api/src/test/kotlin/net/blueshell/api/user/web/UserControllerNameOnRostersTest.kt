package net.blueshell.api.user.web

import net.blueshell.api.user.api.UserUseCases
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class UserControllerNameOnRostersTest {
    private val useCases = mock<UserUseCases>()
    private val controller = UserController(mock(), useCases, mock())

    @Test
    fun `saying whether the name shows answers the account as it now stands`() {
        val user =
            User(username = "alice", email = "a@example.com", password = "h", initials = "A", firstName = "Alice", lastName = "Doe").also {
                it.id = 7
                it.nameOnRosters = true
                it.createdAt = Instant.EPOCH
                it.updatedAt = Instant.EPOCH
            }
        whenever(useCases.setNameOnRosters(7, true)).thenReturn(user)

        assertThat(controller.setNameOnRosters(7, NameOnRostersRequest(shown = true)).nameOnRosters).isTrue()
    }
}
