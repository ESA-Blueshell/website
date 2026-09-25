import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, loginAsDormantBoard} from "./mocks"

test.describe("two-factor", () => {
  test("keeps somebody whose granted role waits for two-factor on the set-up", async ({page}) => {
    await installApiMocks(page)
    await loginAsDormantBoard(page.context())

    await page.goto("/events")

    await expect(page).toHaveURL(/\/account\/set-up-two-factor\?redirect=(%2F|\/)events/)
    await expect(page.getByTestId("two-factor-qr")).toBeVisible()
    await expect(page.getByTestId("two-factor-set-up")).toContainText("Step 1 of 3")
    await expect(page.getByTestId("two-factor-password-field")).toHaveCount(0)
    await expect(page.locator("[data-testid^=account-tab-]")).toHaveCount(0)
    await expect(page.getByTestId("two-factor-sign-out-btn")).toBeVisible()

    await page.goto("/account/games")
    await expect(page).toHaveURL(/\/account\/set-up-two-factor/)
  })

  test("the security hub opens a page for each thing somebody with two-factor can change", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/account/security")
    await expect(page.getByTestId("security-standing-two-factor")).toContainText("10 backup codes left")

    await page.getByTestId("security-two-factor").click()
    await expect(page).toHaveURL(/\/account\/security\/two-factor$/)
    await expect(page.getByTestId("security-backup-codes-left")).toContainText("10 of 10 left")
    await expect(page.getByTestId("security-turn-off-two-factor-btn")).toHaveCount(0)
    await expect(page.getByTestId("account-tab-security")).toHaveAttribute("aria-current", "page")

    await page.getByTestId("account-crumb").click()
    await page.getByTestId("security-password").click()
    await expect(page.getByTestId("security-change-password-btn")).toBeVisible()

    await page.getByTestId("account-crumb").click()
    await page.getByTestId("security-sign-ins").click()
    await expect(page.getByTestId("security-sign-in")).toContainText("This browser")
    await expect(page.getByTestId("security-sign-out-everywhere-btn")).toBeVisible()

    await page.getByTestId("account-crumb").click()
    await page.getByTestId("security-log").click()
    await expect(page.getByTestId("security-log")).toContainText("Nothing in the last twelve months.")
  })

  test("the regular set-up asks for the password before anything else", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/account/security/two-factor/set-up?replace=1")

    await expect(page.locator("h1")).toHaveText("Replace your app")
    await expect(page.getByTestId("two-factor-password-field")).toBeVisible()
    await expect(page.getByTestId("two-factor-start-btn")).toBeDisabled()
  })
})
