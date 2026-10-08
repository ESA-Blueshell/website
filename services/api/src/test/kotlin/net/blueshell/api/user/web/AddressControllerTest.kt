package net.blueshell.api.user.web

import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.AddressFields
import net.blueshell.api.user.api.TestSealing
import net.blueshell.api.user.domain.AddressService
import net.blueshell.api.user.domain.AddressUseCases
import net.blueshell.api.user.persistence.Address
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class AddressControllerTest {
    private val service = mock<AddressService>()
    private val useCases = mock<AddressUseCases>()
    private val controller = AddressController(service, useCases, TestSealing.addresses)
    private val fields = AddressFields("NL", "Enschede", "Hallenweg", "5", "7522NH")
    private val address =
        Address(user = Entities.user(id = 7)).apply {
            id = 11
            createdAt = Instant.EPOCH
            updatedAt = Instant.EPOCH
            TestSealing.addresses.seal(this, fields)
        }

    @Test
    fun `answers a written address opened`() {
        whenever(useCases.create(7, "NL", "Enschede", "Hallenweg", "5", "7522NH")).thenReturn(address)
        whenever(useCases.update(11, "NL", "Enschede", "Hallenweg", "5", "7522NH", 0)).thenReturn(address)

        val created = controller.createAddress(CreateAddressRequest(7, "NL", "Enschede", "Hallenweg", "5", "7522NH"))
        val updated = controller.updateAddress(11, UpdateAddressRequest("NL", "Enschede", "Hallenweg", "5", "7522NH", 0))

        listOf(created, updated).forEach {
            assertThat(it.opened).isTrue()
            assertThat(it.street).isEqualTo("Hallenweg")
            assertThat(it.userId).isEqualTo(7)
        }
    }

    @Test
    fun `opens the one address asked for, and none in the list`() {
        whenever(service.findById(11)).thenReturn(address)
        whenever(service.findAll()).thenReturn(listOf(address))

        assertThat(controller.findAddressById(11).zipCode).isEqualTo("7522NH")
        val listed = controller.findAllAddresses().single()
        assertThat(listed.opened).isFalse()
        assertThat(listOf(listed.country, listed.city, listed.street, listed.houseNumber, listed.zipCode)).containsOnlyNulls()
    }
}
