import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("management pages", () => {
  test("renders member and contribution manager lists", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/members/manage")
    await expect(page.getByTestId("member-user-list-members")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId("member-user-list-non-members")).toBeVisible()

    await page.goto("/contributions/manage")
    await expect(page.getByTestId("contribution-user-list-paid")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId("contribution-user-list-unpaid")).toBeVisible()
  })

  test("renders address and recovery manager lists", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/addresses/manage")
    await expect(page.getByTestId("address-user-list-with-address")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId("address-user-list-without-address")).toBeVisible()

    await page.goto("/recovery/manage")
    await expect(page.getByTestId("recovery-user-list-inactive")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId("recovery-user-list-active")).toBeVisible()
    await expect(page.getByTestId("recovery-user-list-deleted")).toBeVisible()
  })
})
