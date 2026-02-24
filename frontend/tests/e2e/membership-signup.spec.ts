import {expect, test} from "./test"
import {installApiMocks} from "./mocks"
import type {Page} from "@playwright/test"

const inputByTestId = (page: Page, testId: string) =>
  page.getByTestId(testId).locator("input").first()

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

    await page.getByTestId("membership-step1-next-btn").click()

    await expect(page.getByText("This field is required").first()).toBeVisible()
    await expect(page).toHaveURL(/\/membership\/signup$/)
  })

  test("requires privacy-policy agreement before advancing from personal information step", async ({page}) => {
    await installApiMocks(page)
    const suffix = String(Date.now()).slice(-6)
    const phoneSuffix = suffix.slice(-4)

    await page.goto("/membership/signup")
    await expect(page.getByText("MEMBERSHIP FORM", {exact: true})).toBeVisible()

    await inputByTestId(page, "user-form-initials-field").fill("MP")
    await inputByTestId(page, "user-form-first-name-field").fill("Membership")
    await inputByTestId(page, "user-form-last-name-field").fill("Privacy")
    await inputByTestId(page, "user-form-username-field").fill(`member${suffix}`)
    await inputByTestId(page, "user-form-discord-field").fill(`member${suffix}`)
    await inputByTestId(page, "user-form-email-field").fill(`member${suffix}@example.com`)
    await inputByTestId(page, "user-form-phone-number-field").fill(`+3162345${phoneSuffix}`)
    await inputByTestId(page, "user-form-password-field").fill("Password123!")
    await inputByTestId(page, "user-form-password-repeat-field").fill("Password123!")
    await inputByTestId(page, "user-form-date-of-birth-field").fill("2000-01-01")
    await inputByTestId(page, "user-form-student-number-field").fill(`s${suffix}`)

    await page.getByTestId("membership-step1-next-btn").click()

    await expect(page).toHaveURL(/\/membership\/signup$/)
    await expect(page.getByTestId("membership-step1-next-btn")).toBeVisible()

    await inputByTestId(page, "user-form-privacy-consent-field").check()
    await page.getByTestId("membership-step1-next-btn").click()

    await expect(page.getByText("Check your inbox")).toBeVisible()
  })
})
