package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitForSelectorState

/**
 * Helper for member bulk-actions harness. Drives the bulk selection UI
 * (checkboxes, select-all), the bulk actions menu (mark paid / unpaid /
 * send reminder / send incasso / end membership), the bulk action confirm
 * dialog (disposition cells, re-include toggles, amount overrides, date
 * inputs, confirm button), and waits for success.
 */
object MemberManagerBulkHelper {
    // ── Navigation ─────────────────────────────────────────────────────────

    /**
     * Navigate to the member manager and wait for the members table to load.
     */
    fun openMemberManager(page: Page, frontendUrl: String) {
        MemberManagerHelper.open(page, frontendUrl)
        TestIdLocatorHelper.byTestId(page, "member-manager-table").waitFor()
    }

    // ── Row selection ──────────────────────────────────────────────────────

    /**
     * Click the checkbox on a single member row identified by userId.
     * Toggle the row's selection state.
     */
    fun selectUserRow(page: Page, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "member-manager-checkbox-$userId").click()
    }

    /**
     * Click the select-all header checkbox. Toggles between selecting all
     * displayed rows and deselecting all.
     */
    fun selectAllDisplayed(page: Page) {
        TestIdLocatorHelper.byTestId(page, "member-manager-header-checkbox").click()
    }

    /**
     * Check whether the row identified by userId is currently selected.
     */
    fun isRowSelected(page: Page, userId: Long): Boolean {
        val checkbox = TestIdLocatorHelper.byTestId(page, "member-manager-checkbox-$userId")
        val isChecked = checkbox.evaluate("el => el.querySelector('input').checked") as Boolean
        return isChecked
    }

    // ── Bulk menu ──────────────────────────────────────────────────────────

    /**
     * Click the bulk actions menu button (three-dot icon). Assumes
     * at least one row is selected so the button is enabled.
     */
    fun openBulkMenu(page: Page) {
        TestIdLocatorHelper.byTestId(page, "bulk-actions-menu-btn").click()
    }

    /**
     * Choose a bulk action from the menu. The menu must already be open.
     * action is one of: "mark-paid", "mark-unpaid", "send-reminder",
     * "send-incasso", "end-membership".
     */
    fun chooseAction(page: Page, action: String) {
        val testId = when (action) {
            "mark-paid" -> "bulk-action-mark-paid"
            "mark-unpaid" -> "bulk-action-mark-unpaid"
            "send-reminder" -> "bulk-action-send-reminder"
            "send-incasso" -> "bulk-action-send-incasso"
            "end-membership" -> "bulk-action-end-membership"
            else -> error("Unknown action: $action")
        }
        TestIdLocatorHelper.byTestId(page, testId).click()
    }

    // ── Dialog state ───────────────────────────────────────────────────────

    /**
     * Wait for the bulk action confirmation dialog to appear and become
     * ready (preview table loaded, no loading spinner).
     */
    fun waitForDialog(page: Page) {
        TestIdLocatorHelper.byTestId(page, "bulk-action-dialog").waitFor()
        // Wait for the preview table to be visible (loaded)
        TestIdLocatorHelper.byTestId(page, "bulk-action-preview-table").waitFor()
    }

    // ── Per-row disposition ────────────────────────────────────────────────

    /**
     * Read the disposition status of a row in the preview table.
     * Returns one of: "INCLUDED", "EXCLUDED", "WARNING", "SKIPPED".
     * The disposition is rendered as a v-chip with data-testid
     * "bulk-preview-disposition-{userId}".
     */
    fun dispositionOf(page: Page, userId: Long): String {
        val chip = TestIdLocatorHelper.byTestId(page, "bulk-preview-disposition-$userId")
        chip.waitFor()
        // The chip renders the humanized label ("Included"/"Excluded"/"Warning"/
        // "Skipped"); normalize to the BulkRowDisposition enum name callers assert.
        return chip.textContent()?.trim()?.uppercase() ?: ""
    }

    /**
     * Read the reason/note text for a row. Returns the row's reason field
     * or null if not present. This is typically displayed in the "Note"
     * column for EXCLUDED or WARNING rows.
     */
    fun reasonOf(page: Page, userId: Long): String? {
        val noteCell = TestIdLocatorHelper.byTestId(page, "bulk-preview-note-$userId")
        val text = noteCell.textContent()?.trim()
        return if (text.isNullOrEmpty()) null else text
    }

    // ── Re-include override ────────────────────────────────────────────────

    /**
     * Toggle the re-include checkbox for a WARNING row (allows users to
     * override and include a row that was initially excluded/warned).
     * The checkbox is only visible for rows with disposition === "WARNING".
     */
    fun toggleReInclude(page: Page, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "bulk-preview-reinclude-$userId").click()
    }

    // ── Amount override ───────────────────────────────────────────────────

    /**
     * Set the amount override for a row (for send-reminder / send-incasso
     * actions). The amount field is only visible for INCLUDED or
     * re-included WARNING rows.
     */
    fun setAmountOverride(page: Page, userId: Long, amount: String) {
        val field = TestIdLocatorHelper.byTestId(page, "bulk-preview-amount-$userId")
        field.fill(amount)
    }

    // ── Date inputs ────────────────────────────────────────────────────────

    /**
     * Set the payment-due-date input (appears for send-reminder action).
     * Date format: "YYYY-MM-DD" (HTML5 date input).
     */
    fun setPaymentDueDate(page: Page, date: String) {
        TestIdLocatorHelper.textInput(page, "bulk-action-payment-due-date").fill(date)
    }

    /**
     * Set the expected-incasso-date input (appears for send-incasso action).
     * Date format: "YYYY-MM-DD" (HTML5 date input).
     */
    fun setExpectedIncassoDate(page: Page, date: String) {
        TestIdLocatorHelper.textInput(page, "bulk-action-expected-incasso-date").fill(date)
    }

    /**
     * Set the half-year-cutoff-date input (appears for send-reminder and
     * send-incasso actions).
     * Date format: "YYYY-MM-DD" (HTML5 date input).
     */
    fun setCutoffDate(page: Page, date: String) {
        TestIdLocatorHelper.textInput(page, "bulk-action-cutoff-date").fill(date)
    }

    // ── Confirmation ───────────────────────────────────────────────────────

    /**
     * Click the confirm button to execute the bulk action.
     */
    fun confirm(page: Page) {
        TestIdLocatorHelper.byTestId(page, "bulk-action-confirm-btn").click()
    }

    /**
     * Wait for the bulk action to complete successfully. On success the confirm
     * dialog closes, so we wait for the dialog element to become hidden. (A plain
     * isVisible() poll never signals completion: Locator.isVisible returns false
     * without throwing, so it can't detect the element going away.)
     */
    fun waitForSuccess(page: Page, timeoutMs: Long = 10_000) {
        TestIdLocatorHelper.byTestId(page, "bulk-action-dialog").waitFor(
            Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(timeoutMs.toDouble()),
        )
    }
}
