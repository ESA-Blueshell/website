package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class GuestFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable guest`() {
        val guest = guestFactory.createBasic()
        val saved = persist(guest)
        assertPersisted(net.blueshell.api.model.Guest::class.java, saved.id)
    }
}
