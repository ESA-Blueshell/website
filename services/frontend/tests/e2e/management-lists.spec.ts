import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("management pages", () => {
  test("renders user manager table", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/user-manager")
    await expect(page.getByTestId("member-manager-table")).toBeVisible()
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
