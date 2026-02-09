package net.blueshell.api.factory.model.event

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.event.model.Guest
import org.junit.jupiter.api.Test
import kotlin.jvm.java

class GuestFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable guest`() {
        val guest = guestFactory.createBasic()
        val saved = persist(guest)
        assertPersisted(Guest::class.java, saved.id)
    }
}
