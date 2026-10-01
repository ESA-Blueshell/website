import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("the Brevo page", () => {
  test("lists every list by folder with the missing one first, and creates it", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/management/platforms/brevo")

    await expect(page.getByTestId("brevo-missing")).toContainText("1 list the site expects is missing")
    await expect(page.getByTestId("brevo-group-Contribution paid")).toContainText("Paid 2026-2027")
    await expect(page.getByTestId("brevo-state-list-8")).toHaveText("1 missing")
    await expect(page.getByTestId("brevo-group-Follows nothing")).toContainText("Old newsletter test")

    const created = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith("/cohort-targets/BREVO/missing"))
    await page.getByTestId("brevo-create-3").click()
    expect((await created).postDataJSON()).toEqual({targetIds: [3]})

    await page.getByTestId("brevo-row-list-7").getByRole("link").click()
    await expect(page).toHaveURL(/\/management\/platforms\/brevo\/cohort\/101$/)
  })
})
