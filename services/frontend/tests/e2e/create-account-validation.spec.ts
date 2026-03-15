import {expect, test} from "./test"
import {installApiMocks} from "./mocks"
import type {Page} from "@playwright/test"

const inputByTestId = (page: Page, testId: string) =>
  page.getByTestId(testId).locator("input").first()

const phoneInputByTestId = (page: Page, testId: string) =>
  page.getByTestId(testId).getByRole("textbox")

const loadCreateAccountForm = async (page: Page) => {
  const formState = page.getByTestId("create-account-form-state")
  const userForm = page.getByTestId("create-account-user-form")

  await page.goto("/account/create")
  await expect(page).toHaveURL(/\/account\/create$/)

  if (await formState.count() === 0) {
    await page.reload()
    await expect(page).toHaveURL(/\/account\/create$/)
  }

  await expect(formState).toBeVisible()
  await expect(userForm).toBeVisible()
}

test.describe("create account validation", () => {
  test("blocks invalid client-side input before submit", async ({page}) => {
    await installApiMocks(page)
    const suffix = String(Date.now()).slice(-6)

    await loadCreateAccountForm(page)

    await inputByTestId(page, "user-form-initials-field").fill("VA")
    await inputByTestId(page, "user-form-first-name-field").fill("Validation")
    await inputByTestId(page, "user-form-last-name-field").fill("Case")
    await inputByTestId(page, "user-form-username-field").fill(`invalid-user-${suffix}`)
    await inputByTestId(page, "user-form-discord-field").fill(`frontend${suffix}`)
    await inputByTestId(page, "user-form-email-field").fill("not-an-email")
    await phoneInputByTestId(page, "user-form-phone-number-field").fill(`+3164444${suffix}`)
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

  test("requires privacy-policy agreement before account creation submit", async ({page}) => {
    await installApiMocks(page)
    const suffix = String(Date.now()).slice(-6)
    const phoneSuffix = suffix.slice(-4)

    await loadCreateAccountForm(page)

    await inputByTestId(page, "user-form-initials-field").fill("PA")
    await inputByTestId(page, "user-form-first-name-field").fill("Privacy")
    await inputByTestId(page, "user-form-last-name-field").fill("Agreement")
    await inputByTestId(page, "user-form-username-field").fill(`privacy${suffix}`)
    await inputByTestId(page, "user-form-discord-field").fill(`privacy${suffix}`)
    await inputByTestId(page, "user-form-email-field").fill(`privacy${suffix}@example.com`)
    await phoneInputByTestId(page, "user-form-phone-number-field").fill(`+3161234${phoneSuffix}`)
    await inputByTestId(page, "user-form-password-field").fill("Password123!")
    await inputByTestId(page, "user-form-password-repeat-field").fill("Password123!")

    await page.getByTestId("user-form-submit-btn").click()

    await expect(page.getByTestId("create-account-success-state")).toHaveCount(0)
    await expect(page.getByTestId("create-account-form-state")).toBeVisible()

    await inputByTestId(page, "user-form-privacy-consent-field").check()
    await page.getByTestId("user-form-submit-btn").click()

    await expect(page.getByTestId("create-account-success-state")).toBeVisible()
  })
})
