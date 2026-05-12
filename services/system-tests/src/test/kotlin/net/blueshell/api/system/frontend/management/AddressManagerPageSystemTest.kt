package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.system.frontend.helper.AddressFormHelper
import net.blueshell.api.system.frontend.helper.AddressManagerHelper
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners

@Tag("system")
@ActiveProfiles("test")
@TestExecutionListeners(
    listeners = [TestCleanUpListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS,
)
@SpringBootTest(
    classes = [ApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8080", "app.jobs.auto-dispatch=true"],
)
class AddressManagerPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `board adds address for user without address`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val guest = TestHelper.registerActivateAndPromote("GUEST")
        val guestId = TestHelper.findUser(guest.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        AddressManagerHelper.open(page, frontendUrl)
        AddressManagerHelper.openUsersWithoutAddress(page)

        page.getByText(guest.username, Page.GetByTextOptions().setExact(true)).first().waitFor()
        AddressManagerHelper.clickEditAddress(page, guestId)

        AddressFormHelper.fill(
            page = page,
            fields = AddressFormHelper.Fields(
                street = "Campuslaan",
                houseNumber = "12A",
                zipCode = "7522NB",
                city = "Enschede",
            ),
        )

        val createResponse = page.waitForResponse("**/addresses") {
            page.locator("[data-testid='address-form-submit-btn']").first().click()
        }
        assertThat(createResponse.status()).isEqualTo(201)

        val persisted = pollForAddress(guest.username)
        assertThat(persisted.street).isEqualTo("Campuslaan")
        assertThat(persisted.city).isEqualTo("Enschede")
    }

    @Test
    fun `board deletes address for user with address`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val guest = TestHelper.registerActivateAndPromote("GUEST")
        val guestId = TestHelper.findUser(guest.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        AddressManagerHelper.open(page, frontendUrl)
        AddressManagerHelper.openUsersWithoutAddress(page)

        page.getByText(guest.username, Page.GetByTextOptions().setExact(true)).first().waitFor()

        AddressManagerHelper.searchUsersWithoutAddress(page, guest.username)
        AddressManagerHelper.clickEditAddress(page, guestId)
        AddressFormHelper.fill(
            page = page,
            fields = AddressFormHelper.Fields(
                street = "DeleteStreet",
                houseNumber = "77",
                zipCode = "1234AB",
                city = "DeleteCity",
            ),
        )

        val createResponse = page.waitForResponse({ response ->
            response.request().method() == "POST" &&
                response.url().contains("/addresses")
        }) {
            page.locator("[data-testid='address-form-submit-btn']").first().click()
        }
        assertThat(createResponse.status()).isEqualTo(201)
        val addressId = pollForAddress(guest.username).id

        AddressManagerHelper.open(page, frontendUrl)
        AddressManagerHelper.openUsersWithAddress(page)
        page.getByText(guest.username, Page.GetByTextOptions().setExact(true)).first().waitFor()

        AddressManagerHelper.searchUsersWithAddress(page, guest.username)
        AddressManagerHelper.clickDeleteAddress(page, guestId)

        val deleteResponse = page.waitForResponse({ response ->
            response.request().method() == "DELETE" &&
                response.url().contains("/addresses/$addressId")
        }) {
            page.locator("[data-testid='deletion-confirmation-confirm-btn']").first().click()
        }
        assertThat(deleteResponse.status()).isEqualTo(204)

        waitFor("address $addressId deleted") { TestHelper.findAddress(guest.username) == null }
    }

    @Test
    fun `deleted user remains visible in address manager users without address list`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val guest = TestHelper.registerActivateAndPromote("GUEST")
        val guestId = TestHelper.findUser(guest.username)!!.id

        TestHelper.softDeleteUser(guest.username)

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        AddressManagerHelper.open(page, frontendUrl)
        AddressManagerHelper.openUsersWithoutAddress(page)

        page.locator("[data-testid='address-user-row-$guestId']").first().waitFor()
    }

    private fun pollForAddress(username: String): TestHelper.AddressRow {
        val deadline = System.currentTimeMillis() + 6_000
        while (System.currentTimeMillis() < deadline) {
            val row = TestHelper.findAddress(username)
            if (row != null) return row
            Thread.sleep(200)
        }
        throw AssertionError("Expected an address row for $username within 6s")
    }

    private fun waitFor(description: String, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(200)
        }
        throw AssertionError("Expected condition '$description' to hold within 10s")
    }
}
