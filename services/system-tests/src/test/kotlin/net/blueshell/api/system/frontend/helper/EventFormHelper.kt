package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import java.nio.file.Paths

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
        // Wait for the committee list the form loads on mount, so the committee
        // select is populated (and enabled) before any interaction.
        page.waitForResponse("**/committees") {
            page.navigate("$frontendUrl/events/create")
        }
        page.waitForURL("**/events/create**")
        TestIdLocatorHelper.textInput(page, TITLE_FIELD_TEST_ID).waitFor()
    }

    fun openEditPage(page: Page, frontendUrl: String, eventId: Long) {
        page.waitForResponse("**/committees") {
            page.navigate("$frontendUrl/events/edit/$eventId")
        }
        page.waitForURL("**/events/edit/$eventId**")
        TestIdLocatorHelper.textInput(page, TITLE_FIELD_TEST_ID).waitFor()
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
        val committeeField = TestIdLocatorHelper.byTestId(page, COMMITTEE_FIELD_TEST_ID)
        val combo = committeeField.getByRole(AriaRole.COMBOBOX).first()
        val listbox = page.getByRole(AriaRole.LISTBOX).first()
        combo.waitFor()
        // Clicking the select before its list has loaded is a no-op, so retry
        // opening until the options menu actually appears.
        page.waitForCondition {
            if (!listbox.isVisible()) {
                combo.click(Locator.ClickOptions().setForce(true))
            }
            listbox.isVisible()
        }
    }

    fun selectCommittee(page: Page, committeeName: String) {
        openCommitteeSelect(page)
        val option = page.getByText(committeeName, Page.GetByTextOptions().setExact(true)).first()
        option.waitFor()
        option.click()
    }

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
        // Force blur so Vuetify + vee-validate commit the value before submit.
        input.press("Tab")
    }

    fun submit(page: Page) {
        TestIdLocatorHelper.byTestId(page, SUBMIT_BUTTON_TEST_ID).click()
    }
}
