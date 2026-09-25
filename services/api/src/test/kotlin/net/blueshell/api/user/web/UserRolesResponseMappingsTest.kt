package net.blueshell.api.user.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.domain.RoleStanding
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserRolesResponseMappingsTest {
    @Test
    fun `dormant roles are answered by rank`() {
        val standing = RoleStanding(7, emptySet(), emptySet(), emptyMap(), emptySet(), emptySet(), dormant = setOf(Role.ADMIN, Role.BOARD))

        assertThat(standing.asResponse().dormant).containsExactly(Role.BOARD, Role.ADMIN)
    }
}
