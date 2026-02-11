package net.blueshell.api.factory.model

import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.user.persistence.Address
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class AddressFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable address`() {
        val address = addressFactory.createBasic()
        val saved = persist(address)
        assertPersisted(Address::class.java, saved.id)
    }

    @Test
    fun `applies customizer`() {
        val customizer = mockk<Consumer<Address>>(relaxed = true)
        addressFactory.createWithCustomizations(customizer)
        verify(exactly = 1) { customizer.accept(any()) }
    }
}
