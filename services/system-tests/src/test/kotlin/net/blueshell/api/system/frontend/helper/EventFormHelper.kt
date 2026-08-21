package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import java.nio.file.Paths
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import net.blueshell.systemtests.pollFor

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

    fun openCommitteeSelect(page: Page) {
        assertPw(committeeInput(page)).isEnabled()
        committeeField(page).getByRole(AriaRole.COMBOBOX).first().click()
        assertPw(page.getByRole(AriaRole.LISTBOX).first()).isVisible()
    }

    fun selectCommittee(page: Page, committeeName: String) {
        openCommitteeSelect(page)
        val listbox = page.getByRole(AriaRole.LISTBOX).first()
        // The menu is a virtual scroller: it only keeps a window of the
        // committees in the DOM, so an option further down does not exist to be
        // clicked (or waited for) until the list has been scrolled to it. The
        // option is matched inside the menu rather than page-wide, since the
        // name also lands in the select's own selection slot once picked.
        val option = listbox.getByText(committeeName, Locator.GetByTextOptions().setExact(true))
        pollFor("committee '$committeeName' to be rendered in the menu") {
            if (option.count() > 0) {
                true
            } else {
                listbox.evaluate("element => element.scrollBy(0, element.clientHeight)")
                false
            }
        }
        option.first().click()
        // The menu overlays the rest of the form while it closes, and the model
        // holds the committee only once its name is rendered in the select. Both
        // have to settle before the caller fills another field or submits — and
        // asserting the name here turns a mis-clicked option into a failure at
        // the select instead of a submit that silently never fires.
        assertPw(listbox).not().isVisible()
        assertPw(committeeField(page)).containsText(committeeName)
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
        TestIdLocatorHelper.byTestId(page, SUBMIT_BUTTON_TEST_ID).click()
    }
}
