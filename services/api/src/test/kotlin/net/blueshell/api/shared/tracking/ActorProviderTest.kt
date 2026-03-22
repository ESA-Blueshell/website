package net.blueshell.api.shared.tracking

import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ActorProviderTest {

    private val currentUserProvider = mock<CurrentUserProvider>()
    private val provider = ActorProvider(currentUserProvider)

    @Test
    fun `returns user actor when current user exists`() {
        whenever(currentUserProvider.currentUser()).thenReturn(
            CurrentUser(
                id = 42L,
                roles = setOf(Role.MEMBER),
                addressId = null
            )
        )

        val result = provider.currentOrSystem()

        assertThat(result.userId).isEqualTo(42L)
        assertThat(result.type).isEqualTo(ActionActorType.USER)
        assertThat(result.role).isEqualTo(Role.MEMBER)
    }

    @Test
    fun `returns highest available role for current user`() {
        whenever(currentUserProvider.currentUser()).thenReturn(
            CurrentUser(
                id = 42L,
                roles = setOf(Role.GUEST, Role.COMMITTEE, Role.BOARD),
                addressId = null
            )
        )

        val result = provider.currentOrSystem()

        assertThat(result.role).isEqualTo(Role.BOARD)
    }

    @Test
    fun `returns system actor with ADMIN role when no current user exists`() {
        whenever(currentUserProvider.currentUser()).thenReturn(null)

        val result = provider.currentOrSystem()

        assertThat(result.userId).isNull()
        assertThat(result.type).isEqualTo(ActionActorType.SYSTEM)
        assertThat(result.role).isEqualTo(Role.ADMIN)
    }
}
