package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw

/**
 * Drives Vuetify's select and autocomplete fields.
 *
 * A menu renders through `v-virtual-scroll`: only a window of the options is in
 * the DOM, so an option further down a long list does not exist to be clicked
 * or waited for. Fields over data — committees, users, countries — are
 * autocompletes for that reason, and [pickByTyping] reaches an option by
 * narrowing the list to it rather than scrolling or waiting. Short fixed lists
 * (a status, a job type) are plain selects with nothing to type into, so
 * [pickFromList] opens the menu instead.
 *
 * Which one a field is cannot be read off the DOM: `readonly` is absent from
 * both, and `VvField` puts the test id on a wrapper rather than on Vuetify's
 * root, so the class is not there either. The call site names it.
 *
 * Either way the option is matched inside the menu rather than page-wide, and
 * the choice is asserted afterwards, so a pick that never lands fails here
 * instead of at whatever later step needed the value.
 */
object SelectHelper {
    /** Picks `optionText` from an autocomplete by typing it. */
    fun pickByTyping(page: Page, fieldTestId: String, optionText: String) {
        filterBy(page, fieldTestId, optionText)
        take(page, fieldTestId, optionText)
    }

    /** Picks `optionText` from a plain select by opening its menu. */
    fun pickFromList(page: Page, fieldTestId: String, optionText: String) {
        val field = TestIdLocatorHelper.byTestId(page, fieldTestId)
        // The field is disabled while whatever fills it is still in flight, so an
        // enabled field — not the response landing — is the signal that it can be
        // opened: the response arrives a render before the DOM reflects it.
        assertPw(input(page, fieldTestId)).isEnabled()
        field.click()
        take(page, fieldTestId, optionText)
    }

    /**
     * Types `filterText` into an autocomplete and takes the one option it
     * leaves, for pickers whose option label the test has no handle on — a user
     * rendered as "name (discord)" found by email, say. Asserting the menu
     * narrowed to a single option is what makes taking the first one safe.
     */
    fun pickOnlyMatch(page: Page, fieldTestId: String, filterText: String) {
        filterBy(page, fieldTestId, filterText)
        assertMenuOpen(page)
        val options = menu(page).getByRole(AriaRole.OPTION)
        assertPw(options).hasCount(1)
        options.first().click()
        assertMenuClosed(page)
        assertPw(input(page, fieldTestId)).not().hasValue("")
    }

    /**
     * Narrows an autocomplete's menu to `text` without picking anything, and
     * without requiring a match: with `hide-no-data` a filter that matches
     * nothing leaves no menu at all, which is a state callers assert on.
     */
    fun filterBy(page: Page, fieldTestId: String, text: String) {
        val input = input(page, fieldTestId)
        assertPw(input).isEnabled()
        input.fill(text)
    }

    /** The option for `optionText`, scoped to the open menu. */
    fun option(page: Page, optionText: String): Locator =
        menu(page).getByText(optionText, Locator.GetByTextOptions().setExact(true))

    private fun take(page: Page, fieldTestId: String, optionText: String) {
        assertMenuOpen(page)
        option(page, optionText).first().click()
        assertMenuClosed(page)
        assertPw(TestIdLocatorHelper.byTestId(page, fieldTestId)).containsText(optionText)
    }

    private fun menu(page: Page): Locator = page.getByRole(AriaRole.LISTBOX).first()

    private fun assertMenuOpen(page: Page) = assertPw(menu(page)).isVisible()

    // The menu overlays the rest of the form while it fades out, which would
    // otherwise swallow the next click.
    private fun assertMenuClosed(page: Page) = assertPw(menu(page)).not().isVisible()

    private fun input(page: Page, fieldTestId: String): Locator =
        TestIdLocatorHelper.textInput(page, fieldTestId)
}
