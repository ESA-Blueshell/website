package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw

/**
 * Drives the island's searchable picker, which is typed at rather than scrolled through.
 *
 * Its list is drawn at the end of the document, so it is found by the field's own test id
 * rather than by role: a Vuetify menu elsewhere on the page answers to `listbox` as well, and
 * the first one on the page is not necessarily this field's.
 */
object PickerHelper {
    /** The picker settles for 250ms before it asks the api, and the answer is a round trip. */
    private const val OPTION_TIMEOUT_MS = 20_000.0

    fun pickByTyping(
        page: Page,
        fieldTestId: String,
        optionText: String,
    ) {
        filterBy(page, fieldTestId, optionText)
        val option =
            list(page, fieldTestId).getByRole(AriaRole.OPTION).filter(
                Locator.FilterOptions().setHasText(optionText),
            )
        option.first().click(Locator.ClickOptions().setTimeout(OPTION_TIMEOUT_MS))
        assertPw(search(page, fieldTestId)).hasValue(optionText)
    }

    /**
     * Types `filterText` and takes the one row it leaves, for a picker whose row wording the
     * test has no handle on: a member drawn as "name (discord)" but known by their address.
     */
    fun pickOnlyMatch(
        page: Page,
        fieldTestId: String,
        filterText: String,
    ) {
        filterBy(page, fieldTestId, filterText)
        val options = list(page, fieldTestId).getByRole(AriaRole.OPTION)
        assertPw(options).hasCount(1)
        options.first().click(Locator.ClickOptions().setTimeout(OPTION_TIMEOUT_MS))
        assertPw(search(page, fieldTestId)).not().hasValue("")
    }

    fun filterBy(
        page: Page,
        fieldTestId: String,
        text: String,
    ) {
        val search = search(page, fieldTestId)
        assertPw(search).isEnabled()
        search.click()
        search.fill(text)
        assertPw(list(page, fieldTestId)).isVisible()
    }

    private fun search(
        page: Page,
        fieldTestId: String,
    ): Locator = TestIdLocatorHelper.byTestId(page, "$fieldTestId-search").first()

    private fun list(
        page: Page,
        fieldTestId: String,
    ): Locator = TestIdLocatorHelper.byTestId(page, "$fieldTestId-list").first()
}
