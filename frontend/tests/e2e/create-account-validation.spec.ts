import {expect, test} from "./test"
import {installApiMocks} from "./mocks"
import type {Page} from "@playwright/test"

const inputByTestId = (page: Page, testId: string) =>
  page.getByTestId(testId).locator("input").first()

test.describe("create account validation", () => {
  test("blocks invalid client-side input before submit", async ({page}) => {
    await installApiMocks(page)
    const suffix = String(Date.now()).slice(-6)

    await page.goto("/account/create")
    await expect(page.getByTestId("create-account-form-state")).toBeVisible()
    await expect(page.getByTestId("create-account-user-form")).toBeVisible()

    await inputByTestId(page, "user-form-initials-field").fill("VA")
    await inputByTestId(page, "user-form-first-name-field").fill("Validation")
    await inputByTestId(page, "user-form-last-name-field").fill("Case")
    await inputByTestId(page, "user-form-username-field").fill(`invalid-user-${suffix}`)
    await inputByTestId(page, "user-form-discord-field").fill(`frontend${suffix}`)
    await inputByTestId(page, "user-form-email-field").fill("not-an-email")
    await inputByTestId(page, "user-form-phone-number-field").fill(`+3164444${suffix}`)
    await inputByTestId(page, "user-form-password-field").fill("Password123")
    await inputByTestId(page, "user-form-password-repeat-field").fill("Password123")

    await page.getByTestId("user-form-submit-btn").click()

    await expect(page.getByTestId("user-form-username-field").getByText("Use only letters and numbers")).toBeVisible()
    await expect(page.getByTestId("user-form-email-field").getByText("Enter a valid e-mail address")).toBeVisible()
    await expect(
      page.getByTestId("user-form-password-field").getByText("Include a special char (@$!%*?&)")
    ).toBeVisible()
    await expect(page.getByTestId("create-account-success-state")).toHaveCount(0)
  })
})
