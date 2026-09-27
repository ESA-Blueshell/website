package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.systemtests.HttpFailureLog
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw

/** Drives a committee's own edit page, which the board adds, corrects and deletes committees on. */
object CommitteeEditHelper {
    /** The search settles for 250ms before it asks the api, and the answer is a round trip. */
    private const val OPTION_TIMEOUT_MS = 20_000.0

    fun openNew(
        page: Page,
        frontendUrl: String,
    ) {
        page.navigate("$frontendUrl/committees/new")
        TestIdLocatorHelper.byTestId(page, "committee-edit").waitFor()
    }

    fun openEdit(
        page: Page,
        frontendUrl: String,
        address: String,
    ) {
        page.navigate("$frontendUrl/committees/$address/edit")
        TestIdLocatorHelper.byTestId(page, "committee-edit").waitFor()
    }

    fun fillCommittee(
        page: Page,
        name: String,
        description: String,
    ) {
        val nameField = TestIdLocatorHelper.textInput(page, "committee-edit-name")
        nameField.fill(name)
        MarkdownFieldHelper.fillByTestId(page, "committee-edit-description", description)
        assertPw(nameField).hasValue(name)
    }

    /** Finds somebody by what is typed and seats them, then gives them [role]. */
    fun addMember(
        page: Page,
        userId: Long,
        fullName: String,
        role: String,
    ) {
        PickerHelper.filterBy(page, "committee-edit-member", fullName)
        TestIdLocatorHelper
            .byTestId(page, "committee-edit-member-list")
            .getByRole(AriaRole.OPTION)
            .filter(Locator.FilterOptions().setHasText(fullName))
            .first()
            .click(Locator.ClickOptions().setTimeout(OPTION_TIMEOUT_MS))
        TestIdLocatorHelper.textInput(page, "committee-edit-role-$userId").fill(role)
    }

    fun removeMember(
        page: Page,
        userId: Long,
    ) {
        TestIdLocatorHelper.byTestId(page, "committee-edit-unseat-$userId").click()
        assertPw(page.locator("[data-testid='committee-edit-seat-$userId']")).hasCount(0)
    }

    fun save(page: Page) {
        val save = TestIdLocatorHelper.byTestId(page, "committee-edit-save")
        assertPw(save).isEnabled()
        // Marked so a later timeout can say whether the click preceded any request at all.
        HttpFailureLog.mark("committee save click")
        save.click()
    }

    fun delete(page: Page) {
        TestIdLocatorHelper.byTestId(page, "committee-edit-remove").click()
        val dialog = TestIdLocatorHelper.byTestId(page, "committee-remove-dialog")
        TestIdLocatorHelper.byTestId(dialog, "confirm-go").click()
    }
}
