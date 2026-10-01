package net.blueshell.api.discord.web

import net.blueshell.api.discord.domain.RoleOpenings
import net.blueshell.api.discord.persistence.RoleAccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DiscordRoleControllerTest {
    private val openings: RoleOpenings = mock()
    private val controller = DiscordRoleController(openings)

    @Test
    fun `reads, sets, removes, makes and archives what a role opens through the openings`() {
        whenever(openings.read("500")).thenReturn(emptyList())
        whenever(openings.set("500", "1", RoleAccess.READ)).thenReturn(emptyList())
        whenever(openings.remove("500", "1")).thenReturn(emptyList())
        whenever(openings.create("500", "lounge", "Members", RoleAccess.WRITE)).thenReturn(emptyList())
        whenever(openings.archive("500", "1")).thenReturn(emptyList())

        assertThat(controller.listRoleOpenings("500")).isEmpty()
        assertThat(controller.setRoleOpening("500", "1", RoleOpeningRequest(RoleAccess.READ))).isEmpty()
        assertThat(controller.removeRoleOpening("500", "1")).isEmpty()
        assertThat(controller.createRoleChannel("500", RoleChannelRequest("lounge", "Members", RoleAccess.WRITE))).isEmpty()
        assertThat(controller.archiveRoleChannel("500", "1")).isEmpty()
    }
}
