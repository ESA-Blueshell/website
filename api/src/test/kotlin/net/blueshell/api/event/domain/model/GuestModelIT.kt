package net.blueshell.api.event.persistence

import net.blueshell.api.event.web.mapping.asDto
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GuestModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val guest = guestFactory.createBasic()
            guest.name = "Guest"
            guest.discord = "guest#1234"
            guest.email = "guest@example.com"
            guest.phoneNumber = "+31-20-111-2222"
            guest.accessToken = unique("token")

            val found = persistAndReload(guest, Guest::class.java) { it.id }

            assertEquals(guest.name, found.name)
            assertEquals(guest.discord, found.discord)
            assertEquals(guest.email, found.email)
            assertEquals(guest.phoneNumber, found.phoneNumber)
            assertEquals(guest.accessToken, found.accessToken)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted guest`() {
            val guest = persist(guestFactory.createBasic())

            val dto = guest.asDto()

            assertEquals(guest.id, dto.id)
            assertEquals(guest.name, dto.name)
            assertEquals(guest.email, dto.email)
            assertEquals(guest.accessToken, dto.accessToken)
        }
    }
}
