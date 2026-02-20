import {expect, test} from "@playwright/test"
import {installApiMocks} from "./mocks"

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

    await page.getByRole("button", {name: "Next"}).click()

    await expect(page.getByText("This field is required").first()).toBeVisible()
    await expect(page).toHaveURL(/\/membership\/signup$/)
  })
})
