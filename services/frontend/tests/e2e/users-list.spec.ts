import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import {aMembership, aUser} from "./records"

const users = [
  aUser({id: 61, fullName: "Zoë Vermeer", username: "zoe", discordId: "9", addressId: null, enabled: true, roles: ["MEMBER"]}),
  aUser({id: 62, fullName: "Bram Locked", username: "bram", locked: true, enabled: true, roles: ["GUEST"]}),
]

test.describe("the Users list", () => {
  test("finds Zoë by typing zoe, and says why each person needs a look", async ({page}) => {
    await installApiMocks(page, {users, memberships: [aMembership({id: 161, userId: 61, startDate: "2025-01-01"})]})
    await loginAsBoard(page.context())
    await page.setViewportSize({width: 1440, height: 900})
    await page.goto("/management/users")

    await expect(page.getByTestId("member-manager-needs-62")).toContainText("Locked")
    await page.getByTestId("member-manager-search-input").fill("zoe")
    await expect(page.getByTestId("member-manager-row-61")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-62")).toHaveCount(0)
    await expect(page.getByTestId("member-manager-count")).toHaveText("1 of 2 people")
  })

  test("fits a phone, with each row's actions a tap away", {tag: "@phone"}, async ({page}) => {
    await page.setViewportSize({width: 390, height: 844})
    await installApiMocks(page, {users})
    await loginAsBoard(page.context())
    await page.goto("/management/users")

    await page.getByTestId("member-manager-actions-61").click()
    await expect(page.getByTestId("member-manager-edit-profile-btn-61")).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })
})
