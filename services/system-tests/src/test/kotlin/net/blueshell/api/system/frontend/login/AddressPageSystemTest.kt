package net.blueshell.api.system.frontend.login

import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.system.frontend.helper.AddressFormHelper
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.LoginDomainHelper
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
class AddressPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `creates address from account address page`() {
        val user = TestHelper.registerActivateAndPromote("GUEST")

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, user.username, user.password)
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/account/addresses")
        page.waitForURL("**/account/addresses**")

        AddressFormHelper.fill(
            page = page,
            fields = AddressFormHelper.Fields(
                street = "Oude Markt",
                houseNumber = "12",
                zipCode = "7511GA",
                city = "Enschede",
            ),
        )

        val response = page.waitForResponse("**/addresses") {
            LoginDomainHelper.clickAddressSubmit(page)
        }
        assertThat(response.status()).isEqualTo(201)

        val persisted = pollForAddress(user.username)
        assertThat(persisted.street).isEqualTo("Oude Markt")
        assertThat(persisted.houseNumber).isEqualTo("12")
        assertThat(persisted.zipCode).isEqualTo("7511GA")
        assertThat(persisted.city).isEqualTo("Enschede")
        assertThat(persisted.country).isEqualTo("NL")
    }

    @Test
    fun `updates address from account address page`() {
        val user = TestHelper.registerActivateAndPromote("GUEST")
        val addressId = TestHelper.attachAddress(
            user = user,
            country = "NL",
            city = "Oldenzaal",
            street = "Stationsstraat",
            houseNumber = "1",
            zipCode = "7571CE",
        )

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, user.username, user.password)
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
                city = "Enschede",
            ),
        )

        val updateResponse = page.waitForResponse("**/addresses/$addressId") {
            LoginDomainHelper.clickAddressSubmit(page)
        }
        assertThat(updateResponse.status()).isEqualTo(200)

        val updated = pollForAddress(user.username) { it.street == "Boddenkampsingel" }
        assertThat(updated.street).isEqualTo("Boddenkampsingel")
        assertThat(updated.houseNumber).isEqualTo("80")
        assertThat(updated.zipCode).isEqualTo("7514AR")
        assertThat(updated.city).isEqualTo("Enschede")
        assertThat(updated.country).isEqualTo("NL")
    }

    /**
     * Polls until an address row appears for the user (or, optionally,
     * until the row satisfies a predicate — used by the update test to
     * wait for the new street value to land).
     */
    private fun pollForAddress(
        username: String,
        predicate: (TestHelper.AddressRow) -> Boolean = { true },
    ): TestHelper.AddressRow {
        val deadline = System.currentTimeMillis() + 6_000
        while (System.currentTimeMillis() < deadline) {
            val row = TestHelper.findAddress(username)
            if (row != null && predicate(row)) return row
            Thread.sleep(200)
        }
        throw AssertionError("No address row matching predicate for $username within 6s")
    }
}
