import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("the Brevo page", () => {
  test("lists every list by folder with the missing one first, and creates it", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/management/platforms/brevo")

    await expect(page.getByTestId("brevo-missing")).toContainText("1 list the site expects is missing")
    await expect(page.getByTestId("brevo-group-Contribution paid")).toContainText("Paid 2026-2027")
    await expect(page.getByTestId("brevo-state-list-33")).toHaveText("1 missing")
    await expect(page.getByTestId("brevo-group-Follows nothing")).toContainText("Old newsletter test")

    const created = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith("/cohort-targets/BREVO/missing"))
    await page.getByTestId("brevo-create-3").click()
    expect((await created).postDataJSON()).toEqual({targetIds: [3]})

    await page.getByTestId("brevo-row-list-7").getByRole("link").click()
    await expect(page).toHaveURL(/\/management\/platforms\/brevo\/lists\/7$/)
  })

  test("one list shows its drift with why, pushes a missing person, and says what was resolved", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/management/platforms/brevo/lists/7")

    await expect(page.getByTestId("brevo-list")).toContainText("Mail sent to this list reaches Members 2025-2026")
    await expect(page.getByTestId("brevo-list-resolved")).toContainText("Removed from the list")
    const push = page.locator('[data-testid^="brevo-list-push-"]').first()
    await push.click()
    const pushed = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith("/drift/push"))
    await page.getByTestId("brevo-list-plan-confirm").click()
    await pushed
  })

  test("an old link naming a cohort lands on its list", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/management/platforms/brevo/cohort/102")

    await expect(page).toHaveURL(/\/management\/platforms\/brevo\/lists\/33$/)
  })
})
