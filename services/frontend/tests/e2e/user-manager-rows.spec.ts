import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * The rows moved out of the page into their own components. Nothing about them should have
 * changed, which is what this checks: the same test ids, the same controls, the same styling
 * that used to come from the page's scoped stylesheet and now has to come from their own.
 */
test.describe("user manager rows", () => {
  test("the desktop row renders its cells and controls", async ({page}) => {
    await page.setViewportSize({width: 1400, height: 900})
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/user-manager")

    const row = page.locator('[data-testid^="member-manager-row-"]').first()
    await expect(row).toBeVisible()

    const id = (await row.getAttribute("data-testid"))!.replace("member-manager-row-", "")
    await expect(page.getByTestId(`member-manager-checkbox-${id}`)).toBeVisible()
    await expect(page.getByTestId(`member-manager-status-${id}`)).toBeVisible()
    await expect(page.getByTestId(`member-manager-paid-status-${id}`)).toBeVisible()
    await expect(page.getByTestId(`member-manager-period-member-${id}`)).toBeVisible()
    await expect(page.getByTestId(`member-manager-manage-membership-btn-${id}`)).toBeVisible()
    await expect(page.getByTestId(`member-manager-edit-profile-btn-${id}`)).toBeVisible()
    await expect(page.getByTestId(`member-manager-delete-btn-${id}`)).toBeVisible()
  })

  test("the select cell keeps the width the header sets", async ({page}) => {
    await page.setViewportSize({width: 1400, height: 900})
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/user-manager")

    const row = page.locator('[data-testid^="member-manager-row-"]').first()
    await expect(row).toBeVisible()

    // The rule lives in the page's scoped stylesheet and reaches the row through :deep();
    // without that the cell would fall back to the browser's own width.
    const cell = await row.locator("td.mm-select-cell").evaluate((el) => getComputedStyle(el).width)
    expect(cell).toBe("44px")
  })

  test("the narrow layout renders the mobile row", async ({page}) => {
    await page.setViewportSize({width: 600, height: 900})
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/user-manager")

    const row = page.locator('[data-testid^="member-manager-mobile-row-"]').first()
    await expect(row).toBeVisible()

    const id = (await row.getAttribute("data-testid"))!.replace("member-manager-mobile-row-", "")
    await expect(page.getByTestId(`member-manager-mobile-edit-profile-btn-${id}`)).toBeVisible()
    // btn-tight moved into the component's own stylesheet; the page's scoped one cannot
    // reach inside a child.
    const padding = await page.getByTestId(`member-manager-mobile-edit-profile-btn-${id}`)
      .evaluate((el) => getComputedStyle(el).paddingInlineStart)
    expect(padding).toBe("6px")
  })
})
