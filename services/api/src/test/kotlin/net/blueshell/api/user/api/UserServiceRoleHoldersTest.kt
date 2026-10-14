package net.blueshell.api.user.api

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UserServiceRoleHoldersTest {
    @Test
    fun `names everybody holding a role or one above it, once`() {
        val repository = mock<UserRepository>()
        whenever(repository.findIdsHolding(setOf(Role.BOARD, Role.TREASURER, Role.ADMIN, Role.SYSTEM))).thenReturn(listOf(3, 3, 4))
        assertThat(UserService(repository, mock(), mock(), mock()).findIdsHolding(Role.BOARD)).containsExactlyInAnyOrder(3, 4)
    }
}
