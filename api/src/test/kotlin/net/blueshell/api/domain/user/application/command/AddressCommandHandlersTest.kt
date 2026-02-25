package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.AddressService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.CreateAddressCommand
import net.blueshell.api.domain.user.command.DeleteAddressByIdCommand
import net.blueshell.api.domain.user.command.FindAddressByIdCommand
import net.blueshell.api.domain.user.command.FindAllAddressesCommand
import net.blueshell.api.domain.user.command.UpdateAddressCommand
import net.blueshell.api.domain.user.persistence.Address
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AddressCommandHandlersTest {

    private val userService = mock<UserService>()
    private val addressService = mock<AddressService>()

    @Nested
    inner class CreateAddress {

        private val handler = CreateAddressHandler(userService)

        @Test
        fun `creates address and links it to user`() {
            val user = testUser("john")
            whenever(userService.findById(1L)).thenReturn(user)
            whenever(userService.update(user)).thenReturn(user)

            val result = handler.handle(
                CreateAddressCommand(
                    userId = 1L,
                    country = "NL",
                    city = "Utrecht",
                    street = "Main Street",
                    houseNumber = "12A",
                    zipCode = "1234AB"
                )
            )

            assertThat(user.address).isNotNull
            assertThat(user.address?.country).isEqualTo("NL")
            assertThat(user.address?.city).isEqualTo("Utrecht")
            assertThat(user.address?.street).isEqualTo("Main Street")
            assertThat(user.address?.houseNumber).isEqualTo("12A")
            assertThat(user.address?.zipCode).isEqualTo("1234AB")
            assertThat(result).isSameAs(user.address)
        }
    }

    @Nested
    inner class UpdateAddress {

        private val handler = UpdateAddressHandler(addressService)

        @Test
        fun `updates address fields and version`() {
            val address = Address(user = testUser("john"))
            whenever(addressService.findById(2L)).thenReturn(address)
            whenever(addressService.update(address)).thenReturn(address)

            val result = handler.handle(
                UpdateAddressCommand(
                    id = 2L,
                    country = "BE",
                    city = "Ghent",
                    street = "River Road",
                    houseNumber = "99",
                    zipCode = "9000",
                    version = 5L
                )
            )

            assertThat(address.country).isEqualTo("BE")
            assertThat(address.city).isEqualTo("Ghent")
            assertThat(address.street).isEqualTo("River Road")
            assertThat(address.houseNumber).isEqualTo("99")
            assertThat(address.zipCode).isEqualTo("9000")
            assertThat(address.version).isEqualTo(5L)
            assertThat(result).isSameAs(address)
        }
    }

    @Nested
    inner class FindAllAddresses {

        private val handler = FindAllAddressesHandler(addressService)

        @Test
        fun `returns all addresses`() {
            val expected = mutableListOf(Address(user = testUser("john")))
            whenever(addressService.findAll()).thenReturn(expected)

            val result = handler.handle(FindAllAddressesCommand())

            assertThat(result).isSameAs(expected)
            verify(addressService).findAll()
        }
    }

    @Nested
    inner class FindAddressById {

        private val handler = FindAddressByIdHandler(addressService)

        @Test
        fun `returns address by id`() {
            val expected = Address(user = testUser("john"))
            whenever(addressService.findById(3L)).thenReturn(expected)

            val result = handler.handle(FindAddressByIdCommand(3L))

            assertThat(result).isSameAs(expected)
            verify(addressService).findById(3L)
        }
    }

    @Nested
    inner class DeleteAddressById {

        private val handler = DeleteAddressByIdHandler(addressService, userService)

        @Test
        fun `deletes address by clearing user reference`() {
            val user = testUser("jane")
            val address = Address(user = user, country = "NL", city = "Enschede")
            whenever(addressService.findById(4L)).thenReturn(address)
            whenever(userService.update(user)).thenReturn(user)

            handler.handle(DeleteAddressByIdCommand(4L))

            assertThat(user.address).isNull()
            verify(userService).update(user)
        }
    }

    private fun testUser(username: String) = net.blueshell.api.domain.user.persistence.User(
        username = username,
        email = "$username@example.com",
        password = "encoded",
        initials = "JD",
        firstName = "John",
        prefix = null,
        lastName = "Doe",
        phoneNumber = "0612345678",
        discord = "john#0001",
        newsletter = true
    )
}
