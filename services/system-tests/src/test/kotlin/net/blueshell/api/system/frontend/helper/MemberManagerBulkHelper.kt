package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.WaitForSelectorState

/**
 * Helper for member bulk-actions harness. Drives the bulk selection UI
 * (checkboxes, select-all), the bulk actions menu (mark paid / unpaid /
 * send reminder / send incasso / end membership / resume membership), and each
 * per-action confirm dialog (disposition cells, re-include toggles, fee-type
 * overrides, date inputs, confirm button), and waits for success. Every dialog
 * computes its preview rows client-side; the helper never waits on a preview
 * network response, only on the target DOM elements.
 */
object MemberManagerBulkHelper {
    // ── Navigation ─────────────────────────────────────────────────────────

    /**
     * Navigate to the member manager and wait for the members table to load.
     */
    fun openMemberManager(page: Page, frontendUrl: String) {
        UserManagerHelper.open(page, frontendUrl)
        TestIdLocatorHelper.byTestId(page, "member-manager-table").waitFor()
    }

    /**
     * Select a specific contribution period in the member manager's period list.
     * The member manager auto-selects the latest period, so period-sensitive bulk
     * previews (paid status, reminders, incasso) must pin the period the test set
     * up — otherwise, in parallel runs, another period is previewed.
     */
    fun selectPeriod(page: Page, periodId: Long) {
        TestIdLocatorHelper.byTestId(page, "contribution-period-select-btn-$periodId").click()
    }

    // ── Row selection ──────────────────────────────────────────────────────

    /**
     * Bring a member row into the virtualized table's render window.
     *
     * The member table is virtualized (v-data-table-virtual): rows outside the
     * scroll viewport are NOT in the DOM at all, so a locator for an off-screen
     * row never resolves and Playwright cannot auto-scroll to it. Scroll the
     * table's internal scroller from the top downward until the row's element
     * exists (or the scroller bottoms out and the final waitFor reports the
     * genuinely-missing row).
     */
    fun scrollRowIntoView(page: Page, userId: Long) {
        val row = TestIdLocatorHelper.byTestId(page, "member-manager-row-$userId")
        if (row.count() > 0) return
        val scroller = page
            .locator("[data-testid='member-manager-table'] .v-table__wrapper")
            .first()
        scroller.evaluate("el => { el.scrollTop = 0 }")
        var lastTop = -1.0
        while (row.count() == 0) {
            val top = (scroller.evaluate("el => { el.scrollBy(0, 300); return el.scrollTop }") as Number).toDouble()
            if (top == lastTop) break // bottomed out; let the waitFor below fail loudly
            lastTop = top
            // Give the virtualizer a frame to render the shifted window.
            page.waitForTimeout(50.0)
        }
        row.waitFor()
    }

    /**
     * Click the checkbox on a single member row identified by userId.
     * Toggle the row's selection state. Scrolls the virtualized table until
     * the row is rendered first.
     */
    fun selectUserRow(page: Page, userId: Long) {
        scrollRowIntoView(page, userId)
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
     * Scrolls the virtualized table until the row is rendered first.
     */
    fun isRowSelected(page: Page, userId: Long): Boolean {
        scrollRowIntoView(page, userId)
        val checkbox = TestIdLocatorHelper.byTestId(page, "member-manager-checkbox-$userId")
        val isChecked = checkbox.evaluate("el => el.querySelector('input').checked") as Boolean
        return isChecked
    }

    /**
     * Wait until the member row's paid-status chip shows the expected text ("Paid" or
     * "Unpaid"). The mark-paid / mark-unpaid dialogs compute their preview from the page's
     * `paidUserIds`, which the host only refreshes AFTER the previous action's success
     * (via reloadPaid()). Polling the visible chip is the deterministic signal that the
     * refresh landed, so a follow-up bulk action sees the up-to-date paid set rather than
     * racing an in-flight reload. Uses Playwright's auto-retrying text matcher (no manual
     * isVisible polling).
     */
    fun waitForPaidStatus(page: Page, userId: Long, expected: String) {
        scrollRowIntoView(page, userId)
        val chip = TestIdLocatorHelper.byTestId(page, "member-manager-paid-status-$userId")
        // hasText auto-retries until the chip's (trimmed) text equals the expected value,
        // so this deterministically waits out the host's post-action paid reload.
        assertThat(chip).hasText(expected)
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
     * "send-incasso", "end-membership", "resume-membership".
     */
    fun chooseAction(page: Page, action: String) {
        val testId = when (action) {
            "mark-paid" -> "bulk-action-mark-paid"
            "mark-unpaid" -> "bulk-action-mark-unpaid"
            "send-reminder" -> "bulk-action-send-reminder"
            "send-incasso" -> "bulk-action-send-incasso"
            "end-membership" -> "bulk-action-end-membership"
            "resume-membership" -> "bulk-action-resume-membership"
            else -> error("Unknown action: $action")
        }
        TestIdLocatorHelper.byTestId(page, testId).click()
    }

    // ── Dialog state ───────────────────────────────────────────────────────

    /**
     * Wait for the bulk action confirmation dialog to appear.
     *
     * Note: every per-action dialog now computes its rows entirely client-side from
     * the data the page already loaded (users, memberships, contribution periods,
     * paid set) — there are no server preview calls for any action. The preview table
     * is therefore rendered synchronously as soon as the dialog mounts, so this only
     * waits for the dialog shell; the per-row accessors below each wait for their own
     * target element (never a bare isVisible). Reminder / incasso still require the
     * operator to enter dates before the confirm button enables, but the rows and
     * dispositions are visible immediately. See docs/proposals/bulk-actions/REDESIGN.md §5.2.
     */
    fun waitForDialog(page: Page) {
        TestIdLocatorHelper.byTestId(page, "bulk-action-dialog").waitFor()
    }

    /** Explicitly wait for the preview table (rows loaded). */
    fun waitForPreviewTable(page: Page) {
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
        // The data-testid sits on the Vuetify v-checkbox wrapper; click the
        // inner <input> so the bound model actually toggles.
        TestIdLocatorHelper.textInput(page, "bulk-preview-reinclude-$userId").click()
    }

    // ── Fee type selection ─────────────────────────────────────────────────

    /**
     * Choose a fee type in the fee-type selector for a row
     * (for send-reminder / send-incasso actions).
     * feeType must be one of "FULL_YEAR_FEE", "HALF_YEAR_FEE", "ALUMNI_FEE".
     * The select is only visible for INCLUDED or re-included WARNING rows.
     */
    fun chooseFeeType(page: Page, userId: Long, feeType: String) {
        // The v-select wrapper has the data-testid; click to open it, then pick the option.
        val selector = TestIdLocatorHelper.byTestId(page, "bulk-preview-feetype-$userId")
        selector.click()
        // Vuetify renders list items in a portal overlay; match by displayed label
        val label = when (feeType) {
            "FULL_YEAR_FEE" -> "Full-year fee"
            "HALF_YEAR_FEE" -> "Half-year fee"
            "ALUMNI_FEE" -> "Alumni fee"
            else -> feeType
        }
        // Vuetify v-select options are rendered in an overlay with role="option"
        page.locator(".v-list-item:has-text(\"$label\")").first().click()
    }

    /**
     * Read the currently-selected fee type label from a row's fee-type select.
     * Returns the displayed label text (e.g. "Full-year fee").
     * Only meaningful for INCLUDED / re-included WARNING rows.
     */
    fun selectedFeeTypeLabel(page: Page, userId: Long): String {
        val selector = TestIdLocatorHelper.byTestId(page, "bulk-preview-feetype-$userId")
        // The selected value label is rendered inside .v-select__selection or the input's aria-label
        val selectionEl = selector.locator(".v-select__selection-text").first()
        return selectionEl.textContent()?.trim() ?: selector.textContent()?.trim() ?: ""
    }

    /**
     * Read the € amount shown beside the fee-type select for a row. This is the
     * client-resolved amount for the row's currently-selected fee type (looked up
     * from the selected period's fees by bulkCompute / feePreview), shown next to
     * the fee selector for INCLUDED / re-included rows.
     */
    fun amountOf(page: Page, userId: Long): String {
        // The resolved amount lives in its own Amount column, rendered as
        // <span data-testid="bulk-preview-amount-{userId}">€ N</span>. Read it directly by
        // testid — do NOT walk up from the fee-type select, since the fee and amount cells
        // are now separate <td>s and a relative climb catches the wrong cell (e.g. the
        // member-type "Regular"), yielding a NumberFormatException.
        val amountSpan = TestIdLocatorHelper.byTestId(page, "bulk-preview-amount-$userId")
        amountSpan.waitFor()
        val text = amountSpan.textContent()?.trim() ?: ""
        // Strip "€ " prefix
        return text.removePrefix("€ ").trim()
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
