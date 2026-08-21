package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Request
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.options.AriaRole
import java.nio.file.Paths
import java.util.function.Predicate
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import net.blueshell.systemtests.pollFor
import net.blueshell.systemtests.HttpFailureLog

object EventFormHelper {
    private const val TITLE_FIELD_TEST_ID = "event-form-title-field"
    private const val LOCATION_FIELD_TEST_ID = "event-form-location-field"
    private const val DESCRIPTION_FIELD_TEST_ID = "event-form-description-field"
    private const val COMMITTEE_FIELD_TEST_ID = "event-form-committee-field"
    private const val APPROVED_FIELD_TEST_ID = "event-form-approved-field"
    private const val BANNER_FIELD_TEST_ID = "event-form-banner-field"
    private const val SIGNUP_FIELD_TEST_ID = "event-form-signup-field"
    private const val SIGNUP_DEADLINE_FIELD_TEST_ID = "event-form-signup-deadline-field"
    private const val SIGNUP_LIMIT_FIELD_TEST_ID = "event-form-signup-limit-field"
    private const val SUBMIT_BUTTON_TEST_ID = "event-form-submit-btn"

    fun openCreatePage(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/events/create")
        page.waitForURL("**/events/create**")
        waitForFormReady(page)
    }

    fun openEditPage(page: Page, frontendUrl: String, eventId: Long) {
        page.navigate("$frontendUrl/events/edit/$eventId")
        page.waitForURL("**/events/edit/$eventId**")
        waitForFormReady(page)
    }

    /**
     * The form renders its committee select disabled until the committees it
     * requests on mount are in the model, so an enabled select is the signal
     * that the form will accept input. The response landing is not: it arrives
     * a render before the DOM reflects it, and a form filled in that gap keeps
     * an empty `committeeId`, fails its own `required` rule, and swallows the
     * submit — which surfaces as a request that never happens rather than as a
     * validation error.
     */
    fun waitForFormReady(page: Page) {
        TestIdLocatorHelper.textInput(page, TITLE_FIELD_TEST_ID).waitFor()
        assertPw(committeeInput(page)).isEnabled()
    }

    fun fillRequiredFields(
        page: Page,
        title: String,
        location: String,
        description: String
    ) {
        TestIdLocatorHelper.textInput(page, TITLE_FIELD_TEST_ID).fill(title)
        TestIdLocatorHelper.textInput(page, LOCATION_FIELD_TEST_ID).fill(location)
        val descriptionField = TestIdLocatorHelper.byTestId(page, DESCRIPTION_FIELD_TEST_ID)
        val descriptionTextarea = descriptionField.locator("textarea").first()
        if (descriptionTextarea.count() > 0) {
            descriptionTextarea.fill(description)
        } else {
            TestIdLocatorHelper.textInput(page, DESCRIPTION_FIELD_TEST_ID).fill(description)
        }
    }

    fun filterCommittees(page: Page, text: String) {
        SelectHelper.filterBy(page, COMMITTEE_FIELD_TEST_ID, text)
    }

    fun committeeOption(page: Page, committeeName: String): Locator =
        SelectHelper.option(page, committeeName)

    fun selectCommittee(page: Page, committeeName: String) {
        SelectHelper.pickByTyping(page, COMMITTEE_FIELD_TEST_ID, committeeName)
    }

    private fun committeeField(page: Page): Locator =
        TestIdLocatorHelper.byTestId(page, COMMITTEE_FIELD_TEST_ID)

    private fun committeeInput(page: Page): Locator =
        TestIdLocatorHelper.textInput(page, COMMITTEE_FIELD_TEST_ID)

    fun setApproved(page: Page, approved: Boolean) {
        val checkbox = TestIdLocatorHelper.byTestId(page, APPROVED_FIELD_TEST_ID).locator("input[type='checkbox']").first()
        if (approved) {
            checkbox.check()
        } else {
            checkbox.uncheck()
        }
    }

    fun uploadBanner(page: Page, filePath: String) {
        TestIdLocatorHelper.byTestId(page, BANNER_FIELD_TEST_ID).locator("input[type='file']").first()
            .setInputFiles(Paths.get(filePath))
    }

    fun enableSignUp(page: Page) {
        val checkbox = TestIdLocatorHelper.byTestId(page, SIGNUP_FIELD_TEST_ID).locator("input[type='checkbox']").first()
        checkbox.check()
    }

    fun disableSignUp(page: Page) {
        val checkbox = TestIdLocatorHelper.byTestId(page, SIGNUP_FIELD_TEST_ID).locator("input[type='checkbox']").first()
        checkbox.uncheck()
    }

    fun signUpDeadlineInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, SIGNUP_DEADLINE_FIELD_TEST_ID)
    }

    fun signUpLimitInput(page: Page): Locator {
        return TestIdLocatorHelper.textInput(page, SIGNUP_LIMIT_FIELD_TEST_ID)
    }

    fun setSignUpLimit(page: Page, limit: Int) {
        val input = signUpLimitInput(page)
        input.fill(limit.toString())
        // Tabbing out is what a user does, and it is what makes vee-validate run
        // the field's rules; the assertion is what proves the value stuck.
        input.press("Tab")
        assertPw(input).hasValue(limit.toString())
    }

    fun submit(page: Page) {
        val button = TestIdLocatorHelper.byTestId(page, SUBMIT_BUTTON_TEST_ID)
        HttpFailureLog.mark("submit click, buttons=${button.count()}")
        button.click()
    }

    /**
     * Submits and returns the request the form is expected to make.
     *
     * Waits for the request rather than the response: a create or update that
     * succeeds navigates away from the form, and the response event races that
     * teardown — the request has gone out, the server has answered it, and the
     * waiter still times out because the frame it belonged to is gone. The
     * request is delivered before any of that, and what the server made of it
     * is proved by the row the caller goes on to assert.
     *
     * A form that fails its own client-side rules sends nothing at all, and the
     * click reports nothing either, so the failure path reports the page url,
     * whether the form is still mounted, the messages it is showing, and the
     * traffic it produced.
     */
    fun submitExpecting(page: Page, description: String, predicate: (Request) -> Boolean): Request =
        try {
            page.waitForRequest(Predicate { request -> predicate(request) }) { submit(page) }
        } catch (e: TimeoutError) {
            // Only non-waiting reads here: if the submit did land, the form is
            // already gone and anything that auto-waits would time out instead
            // of reporting what happened.
            val messages = page.locator(".v-messages__message").let { locator ->
                if (locator.count() == 0) emptyList() else locator.allTextContents().filter { it.isNotBlank() }
            }
            val submitPresent = TestIdLocatorHelper.byTestId(page, SUBMIT_BUTTON_TEST_ID).count()
            val titlePresent = TestIdLocatorHelper.byTestId(page, TITLE_FIELD_TEST_ID).count()
            throw AssertionError(
                "Submitting the event form produced no $description. " +
                    "url=${page.url()} submitButtons=$submitPresent titleFields=$titlePresent " +
                    "messages=$messages failed=${HttpFailureLog.recent()} " +
                    "requests=${HttpFailureLog.recentRequests()}",
                e,
            )
        }

}
