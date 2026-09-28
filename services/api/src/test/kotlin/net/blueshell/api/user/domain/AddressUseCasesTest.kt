package net.blueshell.api.user.domain

import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.Address
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.OptimisticLockingFailureException

class AddressUseCasesTest {
    private val userService = mock<UserService>()
    private val addressService = mock<AddressService>()
    private val useCases = AddressUseCases(addressService, userService)

    @Nested
    inner class Create {
        @Test
        fun `creates address and links it to user`() {
            val user = testUser("john")
            whenever(userService.findById(1L)).thenReturn(user)
            whenever(userService.update(user)).thenReturn(user)

            val result =
                useCases.create(
                    userId = 1L,
                    country = "NL",
                    city = "Utrecht",
                    street = "Main Street",
                    houseNumber = "12A",
                    zipCode = "1234AB",
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
    inner class Update {
        @Test
        fun `updates address fields, keeping the version it was read at`() {
            val address = Address(user = testUser("john"))
            whenever(addressService.findById(2L)).thenReturn(address)
            whenever(addressService.update(address)).thenReturn(address)

            val result =
                useCases.update(
                    id = 2L,
                    country = "BE",
                    city = "Ghent",
                    street = "River Road",
                    houseNumber = "99",
                    zipCode = "9000",
                    version = 0L,
                )

            assertThat(address.country).isEqualTo("BE")
            assertThat(address.city).isEqualTo("Ghent")
            assertThat(address.street).isEqualTo("River Road")
            assertThat(address.houseNumber).isEqualTo("99")
            assertThat(address.zipCode).isEqualTo("9000")
            assertThat(address.version).isEqualTo(0L)
            assertThat(result).isSameAs(address)
        }

        @Test
        fun `refuses an edit made against an older version, before touching a field`() {
            val address = Address(user = testUser("john")).apply { version = 2L }
            whenever(addressService.findById(2L)).thenReturn(address)

            assertThatThrownBy { useCases.update(2L, "BE", "Ghent", "River Road", "99", "9000", version = 1L) }
                .isInstanceOf(OptimisticLockingFailureException::class.java)
            assertThat(address.city).isNotEqualTo("Ghent")
            verify(addressService, never()).update(any())
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `deletes address by clearing user reference`() {
            val user = testUser("jane")
            val address = Address(user = user, country = "NL", city = "Enschede")
            whenever(addressService.findById(4L)).thenReturn(address)
            whenever(userService.update(user)).thenReturn(user)

            useCases.delete(4L)

            assertThat(user.address).isNull()
            verify(userService).update(user)
        }
    }

    private fun testUser(username: String) =
        User(
            username = username,
            email = "$username@example.com",
            password = "encoded",
            initials = "JD",
            firstName = "John",
            prefix = null,
            lastName = "Doe",
            phoneNumber = "0612345678",
            discord = "john#0001",
            newsletter = true,
        )
}
