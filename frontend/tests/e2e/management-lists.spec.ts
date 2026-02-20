import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("management pages", () => {
  test("renders member and contribution manager lists", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/members/manage")
    await expect(page.getByText(/member manager/i).first()).toBeVisible({timeout: 30_000})
    await expect(page.getByRole("heading", {name: /^Members$/i})).toBeVisible()
    await expect(page.getByRole("heading", {name: /^Non-members$/i})).toBeVisible()

    await page.goto("/contributions/manage")
    await expect(page.getByText(/contribution manager/i).first()).toBeVisible({timeout: 30_000})
    await expect(page.getByRole("heading", {name: /^Contribution paid$/i})).toBeVisible()
    await expect(page.getByRole("heading", {name: /^Contribution unpaid$/i})).toBeVisible()
  })

  test("renders address and recovery manager lists", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/addresses/manage")
    await expect(page.getByText(/address manager/i).first()).toBeVisible({timeout: 30_000})
    await expect(page.getByRole("heading", {name: /^Users with address$/i})).toBeVisible()
    await expect(page.getByRole("heading", {name: /^Users without address$/i})).toBeVisible()

    await page.goto("/recovery/manage")
    await expect(page.getByText(/recovery manager/i).first()).toBeVisible({timeout: 30_000})
    await expect(page.getByRole("heading", {name: /^Inactive accounts$/i})).toBeVisible()
    await expect(page.getByRole("heading", {name: /^Active accounts$/i})).toBeVisible()
  })
})
