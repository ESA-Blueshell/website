import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import {aContributionPeriod, aMembership, aUser, anAddress} from "./records"

test.describe("management filters", () => {
  test("member manager filters users by multiple fields in single table", async ({page}) => {
    await installApiMocks(page, {
      users: [
        aUser({
          id: 31,
          fullName: "Nonmember Filter Target",
          firstName: "NonTarget",
          username: "nonmember-target",
          discord: "nonmember-discord",
          enabled: true,
          roles: ["GUEST"],
        }),
        aUser({
          id: 32,
          fullName: "Nonmember Filter Other",
          firstName: "NonOther",
          username: "nonmember-other",
          discord: "nonmember-other-discord",
          enabled: true,
          roles: ["GUEST"],
        }),
        aUser({
          id: 33,
          fullName: "Member Filter Target",
          firstName: "MemberTarget",
          username: "member-target",
          discord: "member-discord",
          enabled: true,
          roles: ["MEMBER"],
        }),
        aUser({
          id: 34,
          fullName: "Member Filter Other",
          firstName: "MemberOther",
          username: "member-other",
          discord: "member-other-discord",
          enabled: true,
          roles: ["MEMBER"],
        }),
      ],
      memberships: [
        aMembership({id: 131, userId: 33, startDate: "2025-01-01"}),
        aMembership({id: 132, userId: 34, startDate: "2025-01-01"}),
      ],
      contributionPeriods: [
        aContributionPeriod({id: 231, startDate: "2025-01-01", endDate: "2025-12-31", halfYearCutoffDate: "2025-07-01", halfYearFee: 10, fullYearFee: 20, alumniFee: 5}),
      ],
    })
    await loginAsBoard(page.context())

    await page.setViewportSize({width: 1440, height: 900})
    await page.goto("/management/users")
    await expect(page.getByTestId("member-manager-table")).toBeVisible()

    // All users visible before filtering
    await expect(page.getByTestId("member-manager-row-31")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-32")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-33")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-34")).toBeVisible()

    // Filter by name matching only one non-member
    await page.getByTestId("member-manager-search-input").fill("NonTarget")
    await expect(page.getByTestId("member-manager-row-31")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-32")).toHaveCount(0)
    await expect(page.getByTestId("member-manager-row-33")).toHaveCount(0)
    await expect(page.getByTestId("member-manager-row-34")).toHaveCount(0)

    // Filter by first name matching only one member. (Uses the unique "MemberTarget"
    // first name rather than the "member-target" username, which is a substring of
    // non-member "nonmember-target" and would match both in the single table.)
    await page.getByTestId("member-manager-search-input").fill("MemberTarget")
    await expect(page.getByTestId("member-manager-row-33")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-31")).toHaveCount(0)

    // Clear filter — all visible again
    await page.getByTestId("member-manager-search-input").fill("")
    await expect(page.getByTestId("member-manager-row-31")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-34")).toBeVisible()

    // Clear filters empties the search too.
    await page.getByTestId("member-manager-search-input").fill("MemberTarget")
    await expect(page.getByTestId("member-manager-row-31")).toHaveCount(0)
    await page.getByTestId("member-manager-filters-clear").click()
    await expect(page.getByTestId("member-manager-row-31")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-34")).toBeVisible()
  })

  test("Needs a look finds the users with no Discord member linked", async ({page}) => {
    await installApiMocks(page, {
      users: [
        aUser({id: 51, fullName: "Linked Member", username: "linked", discord: "Nelly B", discordId: "803", enabled: true, roles: ["GUEST"]}),
        aUser({id: 52, fullName: "Typed Only", username: "typed", discord: "nelly#0001", enabled: true, roles: ["GUEST"]}),
        aUser({id: 53, fullName: "Nothing Yet", username: "nothing", discord: "", enabled: true, roles: ["GUEST"]}),
      ],
    })
    await loginAsBoard(page.context())
    await page.setViewportSize({width: 1440, height: 900})
    await page.goto("/management/users")
    await expect(page.getByTestId("member-manager-row-51")).toBeVisible()

    await page.getByTestId("member-manager-filter-needs-search").click()
    await page.getByTestId("member-manager-filter-needs-no-discord").click()

    await expect(page.getByTestId("member-manager-row-51")).toHaveCount(0)
    await expect(page.getByTestId("member-manager-row-52")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-53")).toBeVisible()
  })

  test("Needs a look finds the users without an address", async ({page}) => {
    await installApiMocks(page, {
      users: [
        aUser({
          id: 41,
          fullName: "Addressed Filter Target",
          firstName: "AddressTarget",
          username: "address-target",
          email: "address.target@test.com",
          addressId: 501,
          enabled: true,
          roles: ["MEMBER"],
        }),
        aUser({
          id: 42,
          fullName: "Addressed Filter Other",
          firstName: "AddressOther",
          username: "address-other",
          email: "address.other@test.com",
          addressId: 502,
          enabled: true,
          roles: ["MEMBER"],
        }),
        aUser({
          id: 43,
          fullName: "No Address Filter Target",
          firstName: "NoAddressTarget",
          username: "no-address-target",
          email: "no.address.target@test.com",
          enabled: true,
          roles: ["MEMBER"],
        }),
        aUser({
          id: 44,
          fullName: "No Address Filter Other",
          firstName: "NoAddressOther",
          username: "no-address-other",
          email: "no.address.other@test.com",
          enabled: true,
          roles: ["MEMBER"],
        }),
      ],
      addresses: [
        anAddress({id: 501, userId: 41, street: "Main", city: "Enschede", zipCode: "1234AB", country: "NL"}),
        anAddress({id: 502, userId: 42, street: "Main", city: "Enschede", zipCode: "1234AB", country: "NL"}),
      ],
    })
    await loginAsBoard(page.context())

    await page.setViewportSize({width: 1440, height: 900})
    await page.goto("/management/users")
    await page.getByTestId("member-manager-filter-needs-search").click()
    await page.getByTestId("member-manager-filter-needs-no-address").click()

    await expect(page.getByTestId("member-manager-row-43")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-44")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-41")).toHaveCount(0)
    await expect(page.getByTestId("member-manager-row-42")).toHaveCount(0)

    await page.getByTestId("member-manager-search-input").fill("NoAddressTarget no.address.target@test.com")
    await expect(page.getByTestId("member-manager-row-43")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-44")).toHaveCount(0)
  })

  test("Account recovery narrows by search and state", async ({page}) => {
    await installApiMocks(page, {
      users: [
        aUser({
          id: 21,
          fullName: "Inactive Filter Target",
          firstName: "InactiveTarget",
          username: "inactive-target",
          email: "inactive.target@test.com",
          enabled: false,
          roles: ["MEMBER"],
        }),
        aUser({
          id: 22,
          fullName: "Inactive Filter Other",
          firstName: "InactiveOther",
          username: "inactive-other",
          email: "inactive.other@test.com",
          enabled: false,
          roles: ["MEMBER"],
        }),
        aUser({
          id: 23,
          fullName: "Active Filter Target",
          firstName: "ActiveTarget",
          username: "active-target",
          email: "active.target@test.com",
          enabled: true,
          roles: ["MEMBER"],
        }),
        aUser({
          id: 24,
          fullName: "Active Filter Other",
          firstName: "ActiveOther",
          username: "active-other",
          email: "active.other@test.com",
          enabled: true,
          roles: ["MEMBER"],
        }),
      ],
    })
    await loginAsBoard(page.context())

    await page.setViewportSize({width: 1440, height: 900})
    await page.goto("/management/recovery")
    await expect(page.getByTestId("recovery-list")).toBeVisible()

    await page.getByTestId("recovery-search").fill("InactiveTarget inactive.target@test.com")
    await expect(page.getByTestId("recovery-user-row-21")).toBeVisible()
    await expect(page.getByTestId("recovery-user-row-22")).toHaveCount(0)

    await page.getByTestId("recovery-search").fill("")
    await page.getByTestId("recovery-filter-state-search").click()
    await page.getByTestId("recovery-filter-state-active").click()
    await expect(page.getByTestId("recovery-user-row-23")).toBeVisible()
    await expect(page.getByTestId("recovery-user-row-24")).toBeVisible()
    await expect(page.getByTestId("recovery-user-row-21")).toHaveCount(0)
  })
})
