package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.systemtests.HttpFailureLog
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw

object CommitteeFormHelper {
    /** The picker's 250ms settle, plus one api round trip, plus room for a machine under load. */
    private const val OPTION_TIMEOUT_MS = 20_000.0

    fun fillCommittee(page: Page, name: String, description: String) {
        val nameField = page.getByLabel("Committee name")
        val descriptionField = page.getByLabel("Description")
        nameField.fill(name)
        descriptionField.fill(description)
        // The manager fetches its users after the form is already open, and a model
        // replaced by a late arrival takes the typed values with it. Asserted rather
        // than assumed: a save that then sends the old name reaches the api as a
        // perfectly good request, and the stored row cannot say why it is wrong.
        assertPw(nameField).hasValue(name)
        assertPw(descriptionField).hasValue(description)
    }

    fun addMember(page: Page, role: String, fullName: String, index: Int = 0) {
        val hasRoleRows = page.getByLabel("Role").count() > 0
        if (index > 0 || !hasRoleRows) {
            TestIdLocatorHelper.byTestId(page, "committee-form-add-member-btn").click()
        }
        page.getByLabel("Role").nth(index).fill(role)
        val combobox = page.getByRole(
            AriaRole.COMBOBOX,
            Page.GetByRoleOptions().setName("Member name").setExact(false),
        ).nth(index)
        combobox.fill(fullName)
        // Click the matching dropdown option rather than pressing Enter:
        // pressing Enter relies on Vuetify's auto-select-first having
        // populated against the `users` prop, but the parent page
        // loads users asynchronously after mount — on a fresh create
        // form the autocomplete can be empty when this helper runs.
        // Waiting for the option locks the binding without polling.
        //
        // Given longer than the default: the picker settles for 250ms before it asks the api
        // at all, and the answer is a round trip to a container that may be serving this
        // query for the first time. Five seconds covers that on an idle machine and not on a
        // loaded one, which is the whole of why this test failed in CI and never here.
        page.getByRole(
            AriaRole.OPTION,
            Page.GetByRoleOptions().setName(fullName).setExact(false),
        ).first().click(Locator.ClickOptions().setTimeout(OPTION_TIMEOUT_MS))
    }

    fun removeFirstMember(page: Page) {
        TestIdLocatorHelper.byTestIdPrefix(page, "committee-form-remove-member-btn-").click()
    }

    /**
     * What the form is refusing to save on, in its own words.
     *
     * A submit that sends no request at all has been refused by a rule rather than by the api,
     * and the rule that refused is on the page as a message under its field. Read only when a
     * save has already failed to arrive, so that the failure says which field rather than only
     * that nothing was sent.
     */
    fun refusals(page: Page): List<String> =
        page.locator("[data-testid=committee-form] .v-messages__message")
            .allTextContents()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun submit(page: Page) {
        val submitBtn = TestIdLocatorHelper.byTestId(page, "committee-form-submit-btn")
        submitBtn.waitFor()
        // Marked so a later timeout can say whether the click preceded any request
        // at all, which is what separates a refused save from a lost one.
        HttpFailureLog.mark("committee submit click")
        submitBtn.click()
    }
}
