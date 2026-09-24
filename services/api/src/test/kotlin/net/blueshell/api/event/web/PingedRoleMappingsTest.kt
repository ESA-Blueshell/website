package net.blueshell.api.event.web

import net.blueshell.api.event.domain.PingedRoleData
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.PingedRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant

class PingedRoleMappingsTest {
    private val start = Instant.parse("2026-10-10T18:00:00Z")
    private val end = Instant.parse("2026-10-10T21:00:00Z")
    private val gamers = PingedRoleRequest(id = "901", name = "Gamers")

    private fun create(roles: List<PingedRoleRequest>? = null): CreateEventRequest {
        val request =
            CreateEventRequest(
                committeeId = 1,
                title = "LAN",
                description = "Bring a rig.",
                startTime = start,
                endTime = end,
                approved = true,
                membersOnly = false,
                signUp = false,
            )
        return if (roles == null) request else request.copy(pingedRoles = roles)
    }

    private fun update(roles: List<PingedRoleRequest>? = null) =
        UpdateEventRequest(
            committeeId = 1,
            title = "LAN",
            description = "Bring a rig.",
            startTime = start,
            endTime = end,
            approved = true,
            membersOnly = false,
            signUp = false,
            version = 1,
            pingedRoles = roles,
        )

    @Test
    fun `carries the pinged roles a new event names, and none where it names none`() {
        assertThat(create(listOf(gamers)).asData().pingedRoles).containsExactly(PingedRoleData("901", "Gamers"))
        assertThat(create().asData().pingedRoles).isEmpty()
    }

    @Test
    fun `carries an edit's pinged roles, and says nothing of them where the edit leaves them out`() {
        assertThat(update(listOf(gamers)).asData().pingedRoles).containsExactly(PingedRoleData("901", "Gamers"))
        assertThat(update().asData().pingedRoles).isNull()
        assertThat(gamers.id to gamers.name).isEqualTo("901" to "Gamers")
    }

    @Test
    fun `says an event's pinged roles back by name`() {
        val event =
            Event(committee = mock(), title = "LAN", startTime = start, endTime = end).apply {
                pingedRoles += PingedRole("902", "board")
                pingedRoles += PingedRole("901", "Gamers")
            }
        event.id = 7
        event.createdAt = start
        event.updatedAt = start

        val said = event.asResponse().pingedRoles

        assertThat(said.map { it.name }).containsExactly("board", "Gamers")
        assertThat(said.map { it.id }).containsExactly("902", "901")
        assertThat(PingedRole()).isEqualTo(PingedRole("", ""))
    }
}
