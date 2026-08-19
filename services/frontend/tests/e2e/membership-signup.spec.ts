import {expect, test} from "./test"
import {installApiMocks} from "./mocks"
import type {Page} from "@playwright/test"

const inputByTestId = (page: Page, testId: string) =>
  page.getByTestId(testId).locator("input:visible:not([readonly]):not([disabled])").last()

const labeledInputByTestId = (page: Page, testId: string, label: string) =>
  page.getByTestId(testId).getByLabel(label).first()

const fillPersonalInformationStep = async (
  page: Page,
  suffix: string,
  withPrivacyConsent: boolean,
) => {
  const phoneSuffix = suffix.slice(-4)

  await inputByTestId(page, "user-form-initials-field").fill("MP")
  await inputByTestId(page, "user-form-first-name-field").fill("Membership")
  await inputByTestId(page, "user-form-last-name-field").fill("Privacy")
  await inputByTestId(page, "user-form-username-field").fill(`member${suffix}`)
  await inputByTestId(page, "user-form-discord-field").fill(`member${suffix}`)
  await inputByTestId(page, "user-form-email-field").fill(`member${suffix}@example.com`)
  await inputByTestId(page, "user-form-phone-number-field").fill(`+3162345${phoneSuffix}`)
  await labeledInputByTestId(page, "user-form-password-field", "Password*").fill("Password123!")
  await labeledInputByTestId(page, "user-form-password-repeat-field", "Password (repeated)").fill("Password123!")
  await inputByTestId(page, "user-form-date-of-birth-field").fill("2000-01-01")
  await inputByTestId(page, "user-form-student-number-field").fill(`s${suffix}`)

  if (withPrivacyConsent) {
    await inputByTestId(page, "user-form-privacy-consent-field").check()
  }
}

const fillAddressStep = async (page: Page) => {
  await page.getByLabel("Street").first().fill("Drienerlolaan")
  await page.getByLabel("House Number").first().fill("5")
  await page.getByLabel("Zipcode").first().fill("7522NB")
  await page.getByLabel("City").first().fill("Enschede")
}

const acceptConditions = async (page: Page) => {
  await page
    .getByRole("checkbox", {name: /I confirm that I have read and agree to the membership terms/})
    .check()
}

test.describe("membership signup", () => {
  test("navigates from membership page to signup form", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/membership")
    await expect(page.getByText("MEMBERSHIP", {exact: true})).toBeVisible()

    await page.getByRole("button", {name: "Become a member!"}).click()

    await expect(page).toHaveURL(/\/membership\/signup$/)
    await expect(page.getByText("MEMBERSHIP FORM", {exact: true})).toBeVisible()
  })

  test("shows validation messages when personal details are missing", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/membership/signup")
    await expect(page.getByText("MEMBERSHIP FORM", {exact: true})).toBeVisible()

    await page.getByTestId("membership-details-next-btn").click()

    await expect(page.getByText("This field is required").first()).toBeVisible()
    await expect(page.getByTestId("membership-details-next-btn")).toBeVisible()
  })

  test("requires privacy-policy agreement before the account is created", async ({page}) => {
    await installApiMocks(page)
    const blockedSuffix = String(Date.now()).slice(-6)

    await page.goto("/membership/signup")
    await expect(page.getByTestId("membership-signup-stepper")).toBeVisible()

    await fillPersonalInformationStep(page, blockedSuffix, false)

    await page.getByTestId("membership-details-next-btn").click()

    await expect(page.getByTestId("membership-details-next-btn")).toBeVisible()
    await expect(page.getByTestId("membership-address-next-btn")).toHaveCount(0)
    await expect(inputByTestId(page, "user-form-privacy-consent-field")).not.toBeChecked()

    await inputByTestId(page, "user-form-privacy-consent-field").check()

    await page.getByTestId("membership-details-next-btn").click()

    await expect(page.getByTestId("membership-address-next-btn")).toBeVisible()
  })

  test("asks a new applicant to confirm their address once the application is in", async ({page}) => {
    await installApiMocks(page)
    const suffix = String(Date.now()).slice(-6)
    const applicantEmail = `member${suffix}@example.com`

    await page.goto("/membership/signup")
    await fillPersonalInformationStep(page, suffix, true)
    await page.getByTestId("membership-details-next-btn").click()

    await expect(page.getByTestId("membership-address-next-btn")).toBeVisible()
    await fillAddressStep(page)
    await page.getByTestId("membership-address-next-btn").click()

    await expect(page.getByTestId("membership-conditions-submit-btn")).toBeVisible()
    await acceptConditions(page)
    const applyRequest = page.waitForRequest(
      (request) => request.method() === "POST" && request.url().endsWith("/signup/apply"),
    )
    await page.getByTestId("membership-conditions-submit-btn").click()

    // The application travels on the signup token, never on a session.
    expect((await applyRequest).headers()["x-signup-token"]).toBe("e2e-selector.e2e-verifier")

    const confirmStep = page.getByTestId("membership-confirm-email-step")
    await expect(confirmStep).toBeVisible()
    await expect(confirmStep).toContainText(applicantEmail)
    await expect(page.getByTestId("membership-sign-in-btn")).toBeVisible()
    await expect(page.getByTestId("membership-complete-panel")).toHaveCount(0)
  })

  test("lets an applicant correct a mistyped address from the confirmation step", async ({page}) => {
    await installApiMocks(page)
    const suffix = String(Date.now()).slice(-6)

    await page.goto("/membership/signup")
    await fillPersonalInformationStep(page, suffix, true)
    await page.getByTestId("membership-details-next-btn").click()
    await fillAddressStep(page)
    await page.getByTestId("membership-address-next-btn").click()
    await acceptConditions(page)
    await page.getByTestId("membership-conditions-submit-btn").click()

    await expect(page.getByTestId("membership-confirm-email-step")).toBeVisible()
    await page.getByTestId("membership-correct-email-btn").click()

    const correctedField = page.getByTestId("membership-corrected-email-field").locator("input").first()
    await expect(correctedField).toHaveValue(`member${suffix}@example.com`)
    await correctedField.fill(`corrected${suffix}@example.com`)

    const correction = page.waitForRequest(
      (request) => request.method() === "PATCH" && request.url().endsWith("/signup/email"),
    )
    await page.getByTestId("membership-corrected-email-submit-btn").click()

    expect((await correction).headers()["x-signup-token"]).toBe("e2e-selector.e2e-verifier")
    await expect(page.getByTestId("membership-confirm-email-step")).toContainText(`corrected${suffix}@example.com`)
  })
})
