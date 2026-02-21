import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("create account validation", () => {
  test("blocks invalid client-side input before submit", async ({page}) => {
    await installApiMocks(page)
    const suffix = String(Date.now()).slice(-6)

    await page.goto("/account/create")

    await page.getByLabel("Initials*").fill("VA")
    await page.getByLabel("First Name*").fill("Validation")
    await page.getByLabel("Surname*").fill("Case")
    await page.getByLabel("Username*").fill(`invalid-user-${suffix}`)
    await page.getByLabel("Discord*").fill(`frontend${suffix}`)
    await page.getByLabel("E-mail*").fill("not-an-email")
    await page.getByPlaceholder("Phone Number").fill(`+3164444${suffix}`)
    await page.getByLabel("Password*").first().fill("Password123")
    await page.getByLabel("Password (repeated)").fill("Password123")

    await page.getByTestId("user-form-submit-btn").click()

    await expect(page.getByText("Use only letters and numbers")).toBeVisible()
    await expect(page.getByText("Enter a valid e-mail address")).toBeVisible()
    await expect(page.getByText("Include a special char (@$!%*?&)")).toBeVisible()
    await expect(page.getByText("Your account has successfully been created!", {exact: false})).toHaveCount(0)
  })
})
