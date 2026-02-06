package net.blueshell.api.factory.model

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class AddressFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable address`() {
        val address = addressFactory.createBasic()
        val saved = persist(address)
        assertPersisted(net.blueshell.api.model.Address::class.java, saved.id)
    }

    @Test
    fun `applies customizer`() {
        val customizer = mockk<Consumer<net.blueshell.api.model.Address>>(relaxed = true)
        addressFactory.createWithCustomizations(customizer)
        verify(exactly = 1) { customizer.accept(any()) }
    }
}
