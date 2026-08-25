import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("management pages", () => {
  test("renders user manager table", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/user-manager")
    await expect(page.getByTestId("member-manager-table")).toBeVisible()
  })

  test("the user manager calls itself the user manager", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/user-manager")

    // The page manages every account, not only the ones holding a membership — the nav
    // entry has said "Manage users" for a while; the page itself had not caught up.
    // The banner uppercases its title in the markup and carries no heading role, so this
    // matches the text it actually renders.
    await expect(page.getByText("USER MANAGER")).toBeVisible()
    await expect(page.getByTestId("member-manager-table")
      .getByRole("heading", {name: "Users", exact: true})).toBeVisible()
    await expect(page.getByTestId("member-manager-search-input")).toContainText("Search users")
  })

  test("renders address and recovery manager lists", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/addresses/manage")
    await expect(page.getByTestId("address-user-list-with-address")).toBeVisible()
    await expect(page.getByTestId("address-user-list-without-address")).toBeVisible()

    await page.goto("/recovery/manage")
    await expect(page.getByTestId("recovery-user-list-inactive")).toBeVisible()
    await expect(page.getByTestId("recovery-user-list-active")).toBeVisible()
    await expect(page.getByTestId("recovery-user-list-deleted")).toBeVisible()
  })
})
