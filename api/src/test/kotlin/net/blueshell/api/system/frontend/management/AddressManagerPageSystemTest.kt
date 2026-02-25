package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import net.blueshell.api.domain.user.application.erasure.UserErasureService
import net.blueshell.api.domain.user.persistence.repository.AddressRepository
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AddressManagerHelper
import net.blueshell.api.system.frontend.helper.AddressFormHelper
import net.blueshell.api.system.frontend.helper.AuthHelper
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

    @Autowired
    private lateinit var erasure: UserErasureService

    @Test
    fun `board adds address for user without address`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val guest = userFactory.createUserWithRole(Role.GUEST, enabled = true)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            AddressManagerHelper.open(page, frontendUrl)

            AddressManagerHelper.openUsersWithoutAddress(page)

            waitFor(
                onTimeoutMessage = { "Expected ${guest.username} in users without address list" }
            ) {
                page.getByText(guest.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            AddressManagerHelper.clickEditAddress(page, guest.id!!)

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
                page.locator("[data-testid='address-form-submit-btn']").first().click()
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

            AddressManagerHelper.open(page, frontendUrl)

            AddressManagerHelper.openUsersWithoutAddress(page)

            waitFor(
                onTimeoutMessage = { "Expected ${guest.username} in users without address list before creating address" }
            ) {
                page.getByText(guest.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            AddressManagerHelper.searchUsersWithoutAddress(page, guest.username)
            AddressManagerHelper.clickEditAddress(page, guest.id!!)
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
                page.locator("[data-testid='address-form-submit-btn']").first().click()
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

            AddressManagerHelper.open(page, frontendUrl)

            AddressManagerHelper.openUsersWithAddress(page)

            waitFor(
                onTimeoutMessage = { "Expected ${guest.username} in users with address list" }
            ) {
                page.getByText(guest.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            AddressManagerHelper.searchUsersWithAddress(page, guest.username)
            AddressManagerHelper.clickDeleteAddress(page, guest.id!!)

            val deleteResponse = page.waitForResponse({ response ->
                response.request().method() == "DELETE" &&
                    response.url().contains("/addresses/${checkNotNull(addressId)}")
            }) {
                page.locator("[data-testid='deletion-confirmation-confirm-btn']").first().click()
            }
            assertThat(deleteResponse.status()).isEqualTo(204)
        }

        waitFor(
            onTimeoutMessage = { "Expected address ${checkNotNull(addressId)} to be deleted" }
        ) {
            addressRepository.findById(checkNotNull(addressId)).isEmpty
        }
    }

    @Test
    fun `deleted user remains visible in address manager users without address list`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val guest = userFactory.createUserWithRole(Role.GUEST, enabled = true)
        val guestId = checkNotNull(guest.id) { "Expected guest id" }

        erasure.deleteUser(guestId)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            AddressManagerHelper.open(page, frontendUrl)
            AddressManagerHelper.openUsersWithoutAddress(page)

            waitFor(
                onTimeoutMessage = {
                    "Expected deleted user $guestId to stay visible in address manager users without address"
                }
            ) {
                page.locator("[data-testid='address-user-row-$guestId']").count() > 0
            }
        }
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
