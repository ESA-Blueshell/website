package net.blueshell.api.system.frontend.auth

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.EventFormHelper
import net.blueshell.api.system.frontend.helper.EventPageHelper
import net.blueshell.api.system.frontend.helper.LoginDomainHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URLDecoder
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
        val member = TestHelper.registerActivateAndPromote("COMMITTEE")
        val committeeId = TestHelper.createCommittee(name = "Bounce Committee ${TestHelper.uniqueSuffix()}")
        TestHelper.addCommitteeMember(committeeId, member.username)
        val eventId =
            TestHelper.createEvent(
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
        val board = TestHelper.registerActivateAndPromote("BOARD")
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
     * The clock the spa used to keep is not kept any more, so the bounce it caused cannot be
     * staged — there is no stored expiry left to move into the past. What is asserted instead is
     * the state that makes it impossible: the cookie holds who the reader is and nothing that
     * expires, and a signed-in reader reaches a guarded page.
     *
     * How long the api honours a sign-in is not asked here. That is a property of the api, and
     * `SignInLifetimeIT` holds it where it can be asserted directly.
     */
    @Test
    fun `the stored sign-in carries no clock, and a signed-in reader reaches a guarded page`() {
        val member = TestHelper.registerActivateAndPromote("MEMBER")

        assertThat(AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)).isEqualTo(200)

        val stored =
            URLDecoder.decode(
                context.cookies().first { it.name == "login" }.value,
                StandardCharsets.UTF_8,
            )
        assertThat(stored)
            .describedAs("the sign-in as the browser keeps it")
            .doesNotContain("expiration")
            .doesNotContain("\"token\"")
            .contains(member.username)

        page.navigate("$frontendUrl/account")
        page.waitForFunction(
            "() => !window.location.pathname.startsWith('/account') || " +
                "document.querySelector('[data-testid=\"user-form-submit-btn\"]') !== null",
        )
        assertThat(page.url()).describedAs("where the spa landed").doesNotContain("/login")
    }
}
