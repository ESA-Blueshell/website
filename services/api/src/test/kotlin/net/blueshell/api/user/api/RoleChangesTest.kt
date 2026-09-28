package net.blueshell.api.user.api

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.persistence.RoleChange
import net.blueshell.api.user.persistence.RoleChangeRepository
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

class RoleChangesTest {
    private val repository = mock<RoleChangeRepository>()
    private val changes = RoleChanges(repository)

    @Test
    fun `reads a recorded change, and says so where there is none`() {
        val person = User(username = "u", email = "u@example.com", password = "h", initials = "U", firstName = "U", lastName = "U")
        val change = RoleChange(person, person, setOf(Role.MEMBER), setOf(Role.MEMBER, Role.BOARD))
        whenever(repository.findById(7)).thenReturn(Optional.of(change))
        whenever(repository.findById(8)).thenReturn(Optional.empty())

        assertThat(changes.find(7)).isSameAs(change)
        assertThatThrownBy { changes.find(8) }.isInstanceOf(NoSuchElementException::class.java)
    }
}
