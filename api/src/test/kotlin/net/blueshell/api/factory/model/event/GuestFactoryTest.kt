package net.blueshell.api.factory.model.event

import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.factory.model.ModelFactoryTestSupport
import org.junit.jupiter.api.Test

class GuestFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable guest`() {
        val guest = guestFactory.createBasic()
        val saved = persist(guest)
        assertPersisted(Guest::class.java, saved.id)
    }
}
