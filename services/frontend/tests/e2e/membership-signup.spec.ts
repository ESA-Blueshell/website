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

  test("keeps every answer when the applicant walks back and forward again", async ({page}) => {
    await installApiMocks(page)
    const suffix = String(Date.now()).slice(-6)

    await page.goto("/membership/signup")
    await fillPersonalInformationStep(page, suffix, true)
    await page.getByTestId("membership-details-next-btn").click()
    await expect(page.getByTestId("membership-address-next-btn")).toBeVisible()
    await fillAddressStep(page)
    await page.getByTestId("membership-address-next-btn").click()
    await expect(page.getByTestId("membership-conditions-submit-btn")).toBeVisible()

    // All the way back to the first step, then forward again without retyping.
    // A stepper mounts only the active step, so this is where anything a form
    // held privately rather than on the page goes missing.
    await page.getByTestId("membership-conditions-back-btn").click()
    await expect(page.getByLabel("Street").first()).toHaveValue("Drienerlolaan")
    await page.getByTestId("membership-address-back-btn").click()
    await expect(inputByTestId(page, "user-form-date-of-birth-field")).toHaveValue("2000-01-01")
    await expect(inputByTestId(page, "user-form-student-number-field")).toHaveValue(`s${suffix}`)

    // The account exists by now, so there is no password to set and nothing
    // stopping the step from advancing.
    await expect(page.getByTestId("user-form-password-field")).toHaveCount(0)
    await page.getByTestId("membership-details-next-btn").click()
    await expect(page.getByTestId("membership-address-next-btn")).toBeVisible()
    await page.getByTestId("membership-address-next-btn").click()
    await expect(page.getByTestId("membership-conditions-submit-btn")).toBeVisible()
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

    const confirmStep = page.getByTestId("email-confirm-step")
    await expect(confirmStep).toBeVisible()
    await expect(confirmStep).toContainText(applicantEmail)
    await expect(page.getByTestId("email-confirm-sign-in-btn")).toBeVisible()
    await expect(page.getByTestId("membership-complete-panel")).toHaveCount(0)
  })

  test("lets an applicant go back and change their details after applying", async ({page}) => {
    await installApiMocks(page)
    const suffix = String(Date.now()).slice(-6)

    await page.goto("/membership/signup")
    await fillPersonalInformationStep(page, suffix, true)
    await page.getByTestId("membership-details-next-btn").click()
    await fillAddressStep(page)
    await page.getByTestId("membership-address-next-btn").click()
    await acceptConditions(page)
    await page.getByTestId("membership-conditions-submit-btn").click()
    await expect(page.getByTestId("email-confirm-step")).toBeVisible()

    await page.getByTestId("email-confirm-change-details-btn").click()

    // The account exists now, so the details step saves an edit on the token.
    const edit = page.waitForRequest(
      (request) => request.method() === "PATCH" && request.url().endsWith("/signup/details"),
    )
    await inputByTestId(page, "user-form-first-name-field").fill("Corrected")
    await page.getByTestId("membership-details-next-btn").click()

    expect((await edit).headers()["x-signup-token"]).toBe("e2e-selector.e2e-verifier")
  })

  test("keeps the agreement once the application is in", async ({page}) => {
    await installApiMocks(page)
    const suffix = String(Date.now()).slice(-6)

    await page.goto("/membership/signup")
    await fillPersonalInformationStep(page, suffix, true)
    await page.getByTestId("membership-details-next-btn").click()
    await fillAddressStep(page)
    await page.getByTestId("membership-address-next-btn").click()
    await acceptConditions(page)
    await page.getByTestId("membership-conditions-submit-btn").click()
    await expect(page.getByTestId("email-confirm-step")).toBeVisible()

    await page.getByTestId("email-confirm-change-address-btn").click()
    await expect(page.getByTestId("membership-address-next-btn")).toBeVisible()
    await page.getByTestId("membership-address-next-btn").click()

    // Back on the conditions step there is a record of the agreement and no way
    // to withdraw it.
    await expect(page.getByTestId("membership-conditions-accepted")).toBeVisible()
    await expect(page.getByTestId("membership-conditions-submit-btn")).toHaveCount(0)
    await page.getByTestId("membership-conditions-continue-btn").click()
    await expect(page.getByTestId("email-confirm-step")).toBeVisible()
  })

  test("can ask for the confirmation email again", async ({page}) => {
    await installApiMocks(page)
    const suffix = String(Date.now()).slice(-6)

    await page.goto("/membership/signup")
    await fillPersonalInformationStep(page, suffix, true)
    await page.getByTestId("membership-details-next-btn").click()
    await fillAddressStep(page)
    await page.getByTestId("membership-address-next-btn").click()
    await acceptConditions(page)
    await page.getByTestId("membership-conditions-submit-btn").click()

    const resend = page.waitForRequest(
      (request) => request.method() === "POST" && request.url().includes("/recovery/user/activate/resend/"),
    )
    await page.getByTestId("email-confirm-resend-btn").click()

    expect((await resend).url()).toContain(`member${suffix}`)
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

    await expect(page.getByTestId("email-confirm-step")).toBeVisible()
    await page.getByTestId("email-confirm-correct-btn").click()

    const correctedField = page.getByTestId("email-confirm-address-field").locator("input").first()
    await expect(correctedField).toHaveValue(`member${suffix}@example.com`)
    await correctedField.fill(`corrected${suffix}@example.com`)

    const correction = page.waitForRequest(
      (request) => request.method() === "PATCH" && request.url().endsWith("/signup/email"),
    )
    await page.getByTestId("email-confirm-address-submit-btn").click()

    expect((await correction).headers()["x-signup-token"]).toBe("e2e-selector.e2e-verifier")
    await expect(page.getByTestId("email-confirm-step")).toContainText(`corrected${suffix}@example.com`)
  })
})
