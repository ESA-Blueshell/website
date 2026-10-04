import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"
import {aContributionPeriod, aMembership, aUser} from "./records"

// More members than any window holds, so the table has to scroll to show them all.
const COUNT = 300

const users = Array.from({length: COUNT}, (_, i) => aUser({
  id: i + 1,
  // Padded so the default name order matches the numbering.
  fullName: `Member ${String(i + 1).padStart(3, "0")}`,
  username: `member${String(i + 1).padStart(3, "0")}`,
  firstName: "Member",
  lastName: String(i + 1).padStart(3, "0"),
  email: `member${i + 1}@example.com`,
  enabled: true,
  roles: ["MEMBER"],
}))

const memberships = users.map((user, i) => aMembership({
  id: 1000 + i,
  userId: user.id,
  memberType: "REGULAR",
  startDate: "2024-01-01",
  incasso: false,
}))

const contributionPeriods = [
  aContributionPeriod({id: 201, startDate: "2025-07-01", endDate: "2025-12-31", halfYearCutoffDate: "2025-10-01", halfYearFee: 15, fullYearFee: 30, alumniFee: 10}),
]

test.describe("the Users table at full length", () => {
  test("holds every member in a window that scrolls under a head that stays", async ({page}) => {
    await installApiMocks(page, {users, memberships, contributionPeriods, contributions: []})
    await loginAsAdmin(page.context())
    await page.setViewportSize({width: 1440, height: 900})
    await page.goto("/management/users")

    await expect(page.getByTestId("member-manager-count")).toHaveText(`${COUNT} of ${COUNT} people`)
    await expect(page.locator('[data-testid^="member-manager-row-"]')).toHaveCount(COUNT)

    const last = page.getByTestId(`member-manager-row-${COUNT}`)
    await last.scrollIntoViewIfNeeded()
    await expect(last).toBeInViewport()
    await expect(page.getByTestId("member-manager-header-name")).toBeInViewport()
  })
})
