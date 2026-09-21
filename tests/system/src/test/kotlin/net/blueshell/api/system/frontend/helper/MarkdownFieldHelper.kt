package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw

/**
 * Drives the markdown editor the forms write descriptions in.
 *
 * The editor is a contenteditable holding the document itself, so it has no value to fill and
 * none to assert on: what is there is selected and typed over, and read back as text.
 */
object MarkdownFieldHelper {
    fun fillByLabel(
        page: Page,
        label: String,
        text: String,
    ) = fill(page, page.getByLabel(label), text)

    fun fillByTestId(
        page: Page,
        testId: String,
        text: String,
    ) = fill(page, TestIdLocatorHelper.byTestId(page, testId).locator(".cm-content").first(), text)

    private fun fill(
        page: Page,
        editor: Locator,
        text: String,
    ) {
        editor.click()
        page.keyboard().press("ControlOrMeta+a")
        page.keyboard().type(text)
        assertPw(editor).containsText(text)
    }
}
