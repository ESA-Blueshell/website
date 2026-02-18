package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.domain.user.persistence.repository.AddressRepository
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
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
            val loginStatus = loginThroughUi(page, board.username, DEFAULT_PASSWORD)
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

            page.getByLabel("Street", Page.GetByLabelOptions().setExact(true)).fill("Campuslaan")
            page.getByLabel("House Number", Page.GetByLabelOptions().setExact(true)).fill("12A")
            page.getByLabel("Zipcode", Page.GetByLabelOptions().setExact(true)).fill("7522NB")
            page.getByLabel("City", Page.GetByLabelOptions().setExact(true)).fill("Enschede")

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
            val loginStatus = loginThroughUi(page, board.username, DEFAULT_PASSWORD)
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

            page.getByRole(
                AriaRole.TEXTBOX,
                Page.GetByRoleOptions().setName("Search for a user").setExact(false)
            ).first().fill(guest.username)
            page.getByText("Add Address", Page.GetByTextOptions().setExact(false)).first().click()
            page.getByLabel("Street", Page.GetByLabelOptions().setExact(true)).fill("DeleteStreet")
            page.getByLabel("House Number", Page.GetByLabelOptions().setExact(true)).fill("77")
            page.getByLabel("Zipcode", Page.GetByLabelOptions().setExact(true)).fill("1234AB")
            page.getByLabel("City", Page.GetByLabelOptions().setExact(true)).fill("DeleteCity")

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

            page.getByRole(
                AriaRole.TEXTBOX,
                Page.GetByRoleOptions().setName("Search for a user").setExact(false)
            ).first().fill(guest.username)
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

    private fun loginThroughUi(page: Page, username: String, password: String): Int {
        page.navigate("$frontendUrl/login/")
        page.getByLabel("Username").fill(username)
        page.getByRole(
            AriaRole.TEXTBOX,
            Page.GetByRoleOptions().setName("Password")
        ).fill(password)

        val response = page.waitForResponse("**/auth") {
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Login")
            ).click()
        }
        return response.status()
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
