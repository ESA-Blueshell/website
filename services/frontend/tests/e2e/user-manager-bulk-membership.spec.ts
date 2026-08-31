import {expect, test, type Page} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * Ending and starting membership for a selection, from the menu to the result.
 *
 * What is worth driving here rather than asserting in a unit test is the round trip: the
 * dialog does not decide the rows, it asks the api and renders the answer, so a spec that
 * never lets the request happen would be watching a different thing than the board does.
 */

async function openManagerWithSelection(page: Page): Promise<void> {
  await page.setViewportSize({width: 1400, height: 900})
  await installApiMocks(page)
  await loginAsBoard(page.context())
  await page.goto("/user-manager")
  await page.getByTestId("member-manager-table").waitFor()
  await page.getByTestId("member-manager-checkbox-1").locator("input").click()
}

async function openBulkAction(page: Page, testid: string): Promise<void> {
  await page.getByTestId("bulk-actions-menu-btn").click()
  await page.getByTestId(testid).click()
  await page.getByTestId("bulk-action-dialog").waitFor()
}

test.describe("ending and starting membership in bulk", () => {
  test("ending shows the api's rows and date, then reports what it did", async ({page}) => {
    await openManagerWithSelection(page)
    await openBulkAction(page, "bulk-action-end-membership")

    // The date is the api's, so the fixed one the mock answers with is what shows.
    await expect(page.getByTestId("bulk-membership-effective-date")).toContainText("31/08/2026")
    await expect(page.getByTestId("bulk-preview-disposition-1")).toContainText("Included")

    await page.getByTestId("bulk-action-confirm-btn").click()

    await expect(page.getByTestId("bulk-membership-result")).toContainText("1 ended, 0 skipped")
  })

  test("starting names the member it cannot apply to, rather than dropping them", async ({page}) => {
    await openManagerWithSelection(page)
    await openBulkAction(page, "bulk-action-start-membership")

    // The selected member is already active, so starting has nothing to do for them — and
    // says so, in a row that still counts towards the selection.
    await expect(page.getByTestId("bulk-preview-disposition-1")).toContainText("Skipped")
    await expect(page.getByTestId("bulk-preview-note-1")).toContainText("Already has an active membership")
    await expect(page.getByTestId("bulk-action-counts")).toContainText("1 selected")
    await expect(page.getByTestId("bulk-action-counts")).toContainText("0 will apply")
  })

  test("neither action needs a contribution period selected", async ({page}) => {
    await openManagerWithSelection(page)
    await page.getByTestId("bulk-actions-menu-btn").click()

    // Marking contributions is booked against a period; membership is not, so the two
    // membership entries stay live whatever the period picker says.
    await expect(page.getByTestId("bulk-action-end-membership")).not.toHaveClass(/v-list-item--disabled/)
    await expect(page.getByTestId("bulk-action-start-membership")).not.toHaveClass(/v-list-item--disabled/)
  })
})

test.describe("bulk membership without a selection", () => {
  test("the membership actions are offered but inert until somebody is picked", async ({page}) => {
    await page.setViewportSize({width: 1400, height: 900})
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/user-manager")
    await page.getByTestId("member-manager-table").waitFor()

    await page.getByTestId("bulk-actions-menu-btn").click()

    await expect(page.getByTestId("bulk-action-end-membership")).toHaveClass(/v-list-item--disabled/)
    await expect(page.getByTestId("bulk-action-start-membership")).toHaveClass(/v-list-item--disabled/)
  })
})
