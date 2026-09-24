package net.blueshell.api.shared.security

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional

class UserPrincipalMapperTest {
    private val board =
        User(
            username = "bea",
            email = "b@example.com",
            password = "h",
            initials = "B",
            firstName = "Bea",
            lastName = "Board",
            roles = mutableSetOf(Role.MEMBER, Role.BOARD),
        ).also { it.id = 4 }

    @Test
    fun `a principal carries the roles in force, the lock and whether two-factor is on`() {
        val dormant = UserPrincipalMapper.fromUser(board)
        assertThat(dormant.roles).containsExactly(Role.MEMBER)
        assertThat(dormant.hasTwoFactor).isFalse()
        assertThat(dormant.locked).isFalse()

        board.twoFactorSince = Instant.EPOCH
        board.lockedAt = Instant.EPOCH
        val proved = UserPrincipalMapper.fromUser(board)
        assertThat(proved.roles).containsExactlyInAnyOrder(Role.MEMBER, Role.BOARD)
        assertThat(proved.hasTwoFactor).isTrue()
        assertThat(proved.locked).isTrue()
    }

    @Test
    fun `the service reads a principal by id and lists the administrators`() {
        val repository = mock<UserRepository>()
        whenever(repository.findById(4)).thenReturn(Optional.of(board))
        whenever(repository.findAdministrators()).thenReturn(listOf(board))
        val service = UserService(repository, mock(), mock(), mock())

        assertThat(service.loadUserPrincipalById(4).id).isEqualTo(4)
        assertThat(service.findAdministrators()).containsExactly(board)
    }
}
