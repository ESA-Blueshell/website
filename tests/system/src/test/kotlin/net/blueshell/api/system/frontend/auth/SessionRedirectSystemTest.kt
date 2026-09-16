package net.blueshell.api.system.frontend.auth

import com.microsoft.playwright.options.Cookie
import net.blueshell.api.system.frontend.helper.EventFormHelper
import net.blueshell.api.system.frontend.helper.EventPageHelper
import net.blueshell.api.system.frontend.helper.LoginDomainHelper
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Two ways the app sends a signed-in reader back to the login page while the api
 * would still have served them.
 */
@Tag("system")
class SessionRedirectSystemTest : PlaywrightTestBase() {

    /**
     * The guard bounces an anonymous hit on an edit page to `/login?redirect=…`, and signing in
     * pushes the target on top of that entry. Saving the form goes back one entry, which is the
     * login page the reader was bounced through.
     */
    @Test
    fun `saving an event reached through the login bounce stays out of the login page`() {
        val member = TestHelper.registerActivateAndPromote("COMMITTEE", phoneNumber = randomPhoneNumber())
        val committeeId = TestHelper.createCommittee(name = "Bounce Committee ${TestHelper.uniqueSuffix()}")
        TestHelper.addCommitteeMember(committeeId, member.username)
        val eventId = TestHelper.createEvent(
            committeeId = committeeId,
            title = "Bounced Event ${TestHelper.uniqueSuffix()}",
            approved = false,
        )
        val updatedTitle = "Saved After Bounce ${TestHelper.uniqueSuffix()}"

        context.clearCookies()
        page.navigate("$frontendUrl/events/edit/$eventId")
        page.waitForURL("**/login**")

        LoginDomainHelper.fillLoginCredentials(page, member.username, member.password)
        page.waitForResponse({ it.url().contains("/auth") && it.request().method() == "POST" }) {
            LoginDomainHelper.clickLoginSubmit(page)
        }
        page.waitForURL("**/events/edit/$eventId**")

        EventFormHelper.waitForFormReady(page)
        EventFormHelper.fillRequiredFields(page, updatedTitle, "New Location", "Updated description")
        EventFormHelper.submitExpecting(page, "PUT /events/$eventId") { r ->
            r.method() == "PUT" && r.url().contains("/events/$eventId")
        }

        page.waitForURL("**/events**")
        assertThat(page.url())
            .describedAs("where saving the form landed")
            .doesNotContain("/login")
    }

    /**
     * Saving does not pick a page of its own: it returns the reader to the one holding the card
     * they opened, which keeps whatever they had it filtered or scrolled to.
     */
    @Test
    fun `saving an event returns to the page it was opened from`() {
        val board = TestHelper.registerActivateAndPromote("BOARD", phoneNumber = randomPhoneNumber())
        val committeeId = TestHelper.createCommittee(name = "Return Committee ${TestHelper.uniqueSuffix()}")
        val title = "Returning Event ${TestHelper.uniqueSuffix()}"
        val eventId = TestHelper.createEvent(committeeId = committeeId, title = title, approved = true)

        assertThat(AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)).isEqualTo(200)

        page.navigate("$frontendUrl/events")
        EventPageHelper.waitForEventCardVisible(page, eventId)
        EventPageHelper.clickEditEventButton(page, eventId)
        page.waitForURL("**/events/edit/$eventId**")

        EventFormHelper.waitForFormReady(page)
        EventFormHelper.fillRequiredFields(page, "$title edited", "New Location", "Updated description")
        EventFormHelper.submitExpecting(page, "PUT /events/$eventId") { r ->
            r.method() == "PUT" && r.url().contains("/events/$eventId")
        }

        page.waitForURL("**/events")
        assertThat(page.url()).endsWith("/events")
    }

    /**
     * The reader's credentials are untouched and the api would answer every request behind the
     * page; the only thing that has passed is the expiry the sign-in response reported, which the
     * spa kept its own copy of. Reading that copy is what sent readers to the login page a day
     * after they last typed their password, and the guard no longer does.
     *
     * How long the api itself honours a sign-in is not asked here. That is a property of the api,
     * and `SignInLifetimeIT` and `AuthTokenRenewalIT` hold it where it can be asserted directly.
     */
    @Test
    fun `a lapsed token expiry does not send a signed-in reader to the login page`() {
        val member = TestHelper.registerActivateAndPromote("MEMBER", phoneNumber = randomPhoneNumber())

        assertThat(AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)).isEqualTo(200)

        lapseRecordedExpiry()

        page.navigate("$frontendUrl/account")
        page.waitForFunction("() => !window.location.pathname.startsWith('/account') || document.querySelector('[data-testid=\"user-form-submit-btn\"]') !== null")
        assertThat(page.url()).describedAs("where the spa landed").doesNotContain("/login")
    }

    private fun randomPhoneNumber(): String = "06%08d".format(kotlin.random.Random.nextInt(0, 100_000_000))

    /**
     * A day passing, as the spa records it. Only the `login` cookie's `expiration` moves into the
     * past: every credential the api reads is left exactly as it was, so a refusal here could only
     * come from the frontend.
     */
    private fun lapseRecordedExpiry() {
        val cookies = context.cookies()
        val login = cookies.first { it.name == "login" }
        val decoded = URLDecoder.decode(login.value, StandardCharsets.UTF_8)
        val lapsed = decoded.replace(Regex(""""expiration"\s*:\s*\d+"""), """"expiration":${System.currentTimeMillis() - 1000}""")
        check(lapsed != decoded) { "no expiration in the login cookie: $decoded" }

        val kept = cookies.filter { it.name != "login" }
        context.clearCookies()
        context.addCookies(
            kept + Cookie("login", URLEncoder.encode(lapsed, StandardCharsets.UTF_8))
                .setDomain(login.domain)
                .setPath(login.path),
        )
    }
}
