import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, loginAsDormantBoard} from "./mocks"

test.describe("two-factor", () => {
  test("keeps somebody whose granted role waits for two-factor on the set-up", async ({page}) => {
    await installApiMocks(page)
    await loginAsDormantBoard(page.context())

    await page.goto("/events")

    await expect(page).toHaveURL(/\/account\/security\?setUp=1/)
    await expect(page.getByTestId("security-set-up-required")).toBeVisible()
    await expect(page.getByTestId("two-factor-set-up")).toBeVisible()
  })

  test("the security page lists what somebody with two-factor can change", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/account/security")

    await expect(page.getByTestId("security-backup-codes-left")).toContainText("10 backup codes left")
    await expect(page.getByTestId("security-turn-off-two-factor-btn")).toHaveCount(0)
    await expect(page.getByTestId("security-change-password-btn")).toBeVisible()
    await expect(page.getByTestId("security-sign-out-everywhere-btn")).toBeVisible()
  })
})
