package net.blueshell.api.system.frontend.auth

import com.microsoft.playwright.options.Cookie
import net.blueshell.api.system.frontend.helper.EventFormHelper
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
     * The api keeps a reader signed in for the session timeout (30 days by default), rebuilding
     * the security context from the `SESSION` cookie once the 24h auth cookie has lapsed. The spa
     * gates every `requiresAuth` route on the auth token's own expiry instead, so it sends the
     * reader to the login page a day into a session the api would still answer.
     */
    @Test
    fun `a session the api still answers keeps the spa out of the login page`() {
        val member = TestHelper.registerActivateAndPromote("MEMBER", phoneNumber = randomPhoneNumber())
        val userId = TestHelper.findUser(member.username)!!.id

        assertThat(AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)).isEqualTo(200)

        lapseAuthToken()

        val stillAuthorised = context.request().get("$apiUrl/users/$userId")
        assertThat(stillAuthorised.status())
            .describedAs("the api still answers off the SESSION cookie")
            .isEqualTo(200)

        page.navigate("$frontendUrl/account")
        page.waitForFunction("() => !window.location.pathname.startsWith('/account') || document.querySelector('[data-testid=\"user-form-submit-btn\"]') !== null")
        assertThat(page.url()).describedAs("where the spa landed").doesNotContain("/login")
    }

    /**
     * A day passing, as the browser sees it: the auth cookie is gone and the spa's own copy of the
     * token's expiry is in the past. The `SESSION` cookie is left alone, which is what the api reads.
     */
    private fun randomPhoneNumber(): String = "06%08d".format(kotlin.random.Random.nextInt(0, 100_000_000))

    private fun lapseAuthToken() {
        val cookies = context.cookies()
        val login = cookies.first { it.name == "login" }
        val decoded = URLDecoder.decode(login.value, StandardCharsets.UTF_8)
        val lapsed = decoded.replace(Regex(""""expiration"\s*:\s*\d+"""), """"expiration":${System.currentTimeMillis() - 1000}""")
        check(lapsed != decoded) { "no expiration in the login cookie: $decoded" }

        val kept = cookies.filter { it.name != "login" && it.name != "BSH_AUTH" }
        context.clearCookies()
        context.addCookies(
            kept + Cookie("login", URLEncoder.encode(lapsed, StandardCharsets.UTF_8))
                .setDomain(login.domain)
                .setPath(login.path),
        )
    }
}
