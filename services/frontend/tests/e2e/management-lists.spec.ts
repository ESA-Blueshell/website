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

  test("renders the recovery manager lists", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/recovery/manage")
    await expect(page.getByTestId("recovery-user-list-inactive")).toBeVisible()
    await expect(page.getByTestId("recovery-user-list-active")).toBeVisible()
    await expect(page.getByTestId("recovery-user-list-deleted")).toBeVisible()
  })
})
