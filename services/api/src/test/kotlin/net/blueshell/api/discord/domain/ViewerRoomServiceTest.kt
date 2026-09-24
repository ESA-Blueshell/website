package net.blueshell.api.discord.domain

import net.blueshell.api.shared.discord.DiscordLinks
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider

class ViewerRoomServiceTest {
    private fun <T : Any> provided(bean: T?): ObjectProvider<T> = mock { on { ifAvailable } doReturn bean }

    private fun service(
        viewer: CurrentUser?,
        links: DiscordLinks = DiscordLinks { if (it == 7L) "803" else null },
        access: RoomAccess? = RoomAccess { if (it == "803") setOf("12") else emptySet() },
    ) = ViewerRoomService(mock<CurrentUserProvider> { on { currentUser() } doReturn viewer }, provided(links), provided(access))

    private val member = CurrentUser(id = 7, roles = emptySet(), addressId = null)

    @Test
    fun `unlocks what the viewer's own member may join`() {
        assertThat(service(member).rooms()).isEqualTo(ViewerRooms(linked = true, joinable = setOf("12")))
    }

    @Test
    fun `unlocks nothing for somebody logged out, or with no member linked`() {
        assertThat(service(null).rooms()).isEqualTo(ViewerRooms(linked = false, joinable = emptySet()))
        assertThat(service(member.copy(id = 8)).rooms()).isEqualTo(ViewerRooms(linked = false, joinable = emptySet()))
    }

    @Test
    fun `says nothing without the gateway`() {
        assertThat(service(member, access = null).rooms()).isNull()
        assertThat(service(member, access = RoomAccess { null }).rooms()).isNull()
    }
}
