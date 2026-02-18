package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AddressFormHelper
import net.blueshell.api.system.frontend.helper.AuthHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("system")
class AddressPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Test
    fun `creates address from account address page`() {
        val user = userFactory.createUserWithRole(Role.GUEST, enabled = true)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, user.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/account/addresses")
            page.waitForURL("**/account/addresses**")

            AddressFormHelper.fill(
                page = page,
                fields = AddressFormHelper.Fields(
                    street = "Oude Markt",
                    houseNumber = "12",
                    zipCode = "7511GA",
                    city = "Enschede"
                )
            )

            val response = page.waitForResponse("**/addresses") {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Save address").setExact(false)
                ).click()
            }
            assertThat(response.status()).isEqualTo(201)
        }

        val persisted = waitForOptional(
            producer = { userRepository.findByUsername(user.username) },
            onTimeoutMessage = { "Expected user ${user.username} to exist after creating address" }
        )

        assertThat(persisted.address).isNotNull
        assertThat(persisted.address?.street).isEqualTo("Oude Markt")
        assertThat(persisted.address?.houseNumber).isEqualTo("12")
        assertThat(persisted.address?.zipCode).isEqualTo("7511GA")
        assertThat(persisted.address?.city).isEqualTo("Enschede")
        assertThat(persisted.address?.country).isEqualTo("NL")
    }

    @Test
    fun `updates address from account address page`() {
        val user = userFactory.createUserWithRole(Role.GUEST, enabled = true)
        user.replaceAddress(
            userFactory.buildAddress(
                user = user,
                country = "NL",
                city = "Oldenzaal",
                street = "Stationsstraat",
                houseNumber = "1",
                zipCode = "7571CE"
            )
        )
        userRepository.saveAndFlush(user)

        val withAddress = waitForOptional(
            producer = { userRepository.findByUsername(user.username) },
            onTimeoutMessage = { "Expected user ${user.username} with address before update test" }
        )
        val addressId = checkNotNull(withAddress.addressId) { "Expected persisted address id for ${user.username}" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, user.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            val loadResponse = page.waitForResponse("**/addresses/$addressId") {
                page.navigate("$frontendUrl/account/addresses/$addressId")
            }
            assertThat(loadResponse.status()).isEqualTo(200)
            page.waitForURL("**/account/addresses/**")

            AddressFormHelper.fill(
                page = page,
                fields = AddressFormHelper.Fields(
                    street = "Boddenkampsingel",
                    houseNumber = "80",
                    zipCode = "7514AR",
                    city = "Enschede"
                )
            )

            val updateResponse = page.waitForResponse("**/addresses/$addressId") {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Save address").setExact(false)
                ).click()
            }
            assertThat(updateResponse.status()).isEqualTo(200)
        }

        val updated = waitForOptional(
            producer = { userRepository.findByUsername(user.username) },
            onTimeoutMessage = { "Expected user ${user.username} after updating address" }
        )

        assertThat(updated.address).isNotNull
        assertThat(updated.address?.street).isEqualTo("Boddenkampsingel")
        assertThat(updated.address?.houseNumber).isEqualTo("80")
        assertThat(updated.address?.zipCode).isEqualTo("7514AR")
        assertThat(updated.address?.city).isEqualTo("Enschede")
        assertThat(updated.address?.country).isEqualTo("NL")
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
