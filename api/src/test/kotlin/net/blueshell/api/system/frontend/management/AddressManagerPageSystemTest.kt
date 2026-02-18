package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.domain.user.persistence.repository.AddressRepository
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AddressFormHelper
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.UserListHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("system")
class AddressManagerPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var addressRepository: AddressRepository

    @Test
    fun `board adds address for user without address`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val guest = userFactory.createUserWithRole(Role.GUEST, enabled = true)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/addresses/manage")
            page.waitForURL("**/addresses/manage**")

            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Users without address").setExact(false)
            ).click()

            waitFor(
                onTimeoutMessage = { "Expected ${guest.username} in users without address list" }
            ) {
                page.getByText(guest.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            page.getByText(guest.username, Page.GetByTextOptions().setExact(true)).first().click()

            AddressFormHelper.fill(
                page = page,
                fields = AddressFormHelper.Fields(
                    street = "Campuslaan",
                    houseNumber = "12A",
                    zipCode = "7522NB",
                    city = "Enschede"
                )
            )

            val createResponse = page.waitForResponse("**/addresses") {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Save Address").setExact(false)
                ).click()
            }
            assertThat(createResponse.status()).isEqualTo(201)
        }

        waitFor(
            onTimeoutMessage = { "Expected address to be persisted for ${guest.username}" }
        ) {
            addressRepository.findAll().any { it.user.id == guest.id }
        }
        val persisted = addressRepository.findAll().first { it.user.id == guest.id }
        assertThat(persisted.street).isEqualTo("Campuslaan")
        assertThat(persisted.city).isEqualTo("Enschede")
    }

    @Test
    fun `board deletes address for user with address`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val guest = userFactory.createUserWithRole(Role.GUEST, enabled = true)
        var addressId: Long? = null

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/addresses/manage")
            page.waitForURL("**/addresses/manage**")

            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Users without address").setExact(false)
            ).click()

            waitFor(
                onTimeoutMessage = { "Expected ${guest.username} in users without address list before creating address" }
            ) {
                page.getByText(guest.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            UserListHelper.searchUser(page, guest.username)
            page.getByText("Add Address", Page.GetByTextOptions().setExact(false)).first().click()
            AddressFormHelper.fill(
                page = page,
                fields = AddressFormHelper.Fields(
                    street = "DeleteStreet",
                    houseNumber = "77",
                    zipCode = "1234AB",
                    city = "DeleteCity"
                )
            )

            val createResponse = page.waitForResponse({ response ->
                response.request().method() == "POST" &&
                    response.url().contains("/addresses")
            }) {
                page.getByText("Save Address", Page.GetByTextOptions().setExact(false)).first().click()
            }
            assertThat(createResponse.status()).isEqualTo(201)
            waitFor(
                onTimeoutMessage = { "Expected created address for ${guest.username} to be persisted before delete flow" }
            ) {
                addressRepository.findAll().any { it.user.id == guest.id }
            }
            addressId = checkNotNull(
                addressRepository.findAll().firstOrNull { it.user.id == guest.id }?.id
            ) { "Expected created address id for ${guest.username}" }

            page.navigate("$frontendUrl/addresses/manage")
            page.waitForURL("**/addresses/manage**")

            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Users with address").setExact(false)
            ).click()

            waitFor(
                onTimeoutMessage = { "Expected ${guest.username} in users with address list" }
            ) {
                page.getByText(guest.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            UserListHelper.searchUser(page, guest.username)
            page.getByText("Delete Address", Page.GetByTextOptions().setExact(false)).first().click()

            val deleteResponse = page.waitForResponse({ response ->
                response.request().method() == "DELETE" &&
                    response.url().contains("/addresses/${checkNotNull(addressId)}")
            }) {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Delete").setExact(true)
                ).click()
            }
            assertThat(deleteResponse.status()).isEqualTo(204)
        }

        waitFor(
            onTimeoutMessage = { "Expected address ${checkNotNull(addressId)} to be deleted" }
        ) {
            addressRepository.findById(checkNotNull(addressId)).isEmpty
        }
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
