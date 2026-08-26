import {expect, test, type Page} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * The rows live in their own components now, which moves two things out of the page: the events
 * a control has to raise to reach the page's state, and the rules that style what it renders.
 * A parent's scoped stylesheet cannot reach inside a child, so styling that used to be applied
 * from the page would fail silently — everything still renders, just unstyled. These assert the
 * result rather than the declaration.
 */
async function openTable(page: Page, {narrow = false} = {}): Promise<void> {
  await page.setViewportSize(narrow ? {width: 600, height: 900} : {width: 1400, height: 900})
  await installApiMocks(page)
  await loginAsBoard(page.context())
  await page.goto("/user-manager")
  await page.getByTestId("member-manager-table").waitFor()
}

const firstRow = (page: Page) => page.locator('[data-testid^="member-manager-row-"]').first()

async function rowId(page: Page, prefix = "member-manager-row-"): Promise<string> {
  const row = page.locator(`[data-testid^="${prefix}"]`).first()
  await row.waitFor()
  return (await row.getAttribute("data-testid"))!.replace(prefix, "")
}

test.describe("user manager rows", () => {
  test("the desktop row renders its cells and controls", async ({page}) => {
    await openTable(page)
    const id = await rowId(page)

    await expect(page.getByTestId(`member-manager-checkbox-${id}`)).toBeVisible()
    await expect(page.getByTestId(`member-manager-status-${id}`)).toBeVisible()
    await expect(page.getByTestId(`member-manager-member-since-${id}`)).toBeVisible()
    await expect(page.getByTestId(`member-manager-period-member-${id}`)).toBeVisible()
    await expect(page.getByTestId(`member-manager-paid-status-${id}`)).toBeVisible()
    await expect(page.getByTestId(`member-manager-manage-membership-btn-${id}`)).toBeVisible()
    await expect(page.getByTestId(`member-manager-edit-profile-btn-${id}`)).toBeVisible()
    await expect(page.getByTestId(`member-manager-delete-btn-${id}`)).toBeVisible()
  })

  test("selecting a row reaches the page, which owns the selection", async ({page}) => {
    await openTable(page)
    const id = await rowId(page)

    await page.getByTestId(`member-manager-checkbox-${id}`).locator("input").click()

    // The row emits an id; the page decides what is selected and marks the row. Seeing the
    // mark means the event crossed the component boundary and came back as state.
    await expect(firstRow(page)).toHaveClass(/mm-row--selected/)
  })

  test("the header and the body agree on the checkbox column", async ({page}) => {
    await openTable(page)
    await firstRow(page).waitFor()

    // The width is the table's to decide — asserting a number here would pin the layout rather
    // than the invariant. What matters is that the two cells share it, since the rule that
    // centres them lives in two files now.
    const header = await page.locator("th.mm-select-cell").first()
      .evaluate((el) => getComputedStyle(el).width)
    const body = await firstRow(page).locator("td.mm-select-cell")
      .evaluate((el) => getComputedStyle(el).width)

    expect(body).toBe(header)
  })

  test("the row's checkbox cell is centred by its own stylesheet", async ({page}) => {
    await openTable(page)
    await firstRow(page).waitFor()

    // Came from the page's scoped stylesheet before the extraction, so it had to move with the
    // markup; if it had not, this reads as the browser default.
    const centred = await firstRow(page).locator("td.mm-select-cell")
      .evaluate((el) => getComputedStyle(el).textAlign)

    expect(centred).toBe("center")
  })

  test("the narrow layout renders the mobile row and keeps its tight buttons", async ({page}) => {
    await openTable(page, {narrow: true})
    const id = await rowId(page, "member-manager-mobile-row-")

    const edit = page.getByTestId(`member-manager-mobile-edit-profile-btn-${id}`)
    await expect(edit).toBeVisible()

    // `.btn-tight` moved into the component's own stylesheet — four actions have to fit beside
    // a name on a narrow screen, and the page's scoped rule cannot reach in here.
    expect(await edit.evaluate((el) => getComputedStyle(el).paddingInlineStart)).toBe("6px")
  })
})
