import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("management pages", () => {
  test("renders user manager table", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management/users")
    await expect(page.getByTestId("member-manager-table")).toBeVisible()
  })

  test("the Users page names itself and what its search box finds", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management/users")

    await expect(page.getByTestId("member-manager-table")
      .getByRole("heading", {name: "Users", exact: true})).toBeVisible()
    await expect(page.getByTestId("member-manager-search-input")).toHaveAttribute("placeholder", "Search for a user")
  })

  test("Account recovery lists the accounts, and the old address manager leads to Users", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management/recovery")
    await expect(page.getByTestId("recovery-manager")).toBeVisible()
    await expect(page.getByTestId("recovery-list")).toBeVisible()

    await page.goto("/management/addresses")
    await expect(page).toHaveURL(/\/management\/users$/)
  })
})
