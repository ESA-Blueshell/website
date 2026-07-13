import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import type {Locator, Page} from "@playwright/test"

const escapeRegExp = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")

const exactText = (value: string) => new RegExp(`^${escapeRegExp(value)}$`)

const listCard = (page: Page, cardTestId: string): Locator =>
  page.getByTestId(cardTestId).first()

const searchInput = (page: Page, searchTestId: string): Locator =>
  page.getByTestId(searchTestId).locator("input").first()

const ensureListOpen = async (
  page: Page,
  cardTestId: string,
  toggleTestId: string,
  searchTestId: string,
): Promise<Locator> => {
  const card = listCard(page, cardTestId)
  const headerToggle = page.getByTestId(toggleTestId).first()

  if (await headerToggle.getAttribute("aria-expanded") !== "true") {
    await headerToggle.click()
  }

  await expect(page.getByTestId(searchTestId)).toBeVisible()
  return card
}

test.describe("management filters", () => {
  test("member manager filters users by multiple fields in single table", async ({page}) => {
    await installApiMocks(page, {
      users: [
        {
          id: 31,
          fullName: "Nonmember Filter Target",
          firstName: "NonTarget",
          username: "nonmember-target",
          discord: "nonmember-discord",
          enabled: true,
          roles: ["USER"],
        },
        {
          id: 32,
          fullName: "Nonmember Filter Other",
          firstName: "NonOther",
          username: "nonmember-other",
          discord: "nonmember-other-discord",
          enabled: true,
          roles: ["USER"],
        },
        {
          id: 33,
          fullName: "Member Filter Target",
          firstName: "MemberTarget",
          username: "member-target",
          discord: "member-discord",
          enabled: true,
          roles: ["MEMBER"],
        },
        {
          id: 34,
          fullName: "Member Filter Other",
          firstName: "MemberOther",
          username: "member-other",
          discord: "member-other-discord",
          enabled: true,
          roles: ["MEMBER"],
        },
      ],
      memberships: [
        {id: 131, userId: 33, startDate: "2025-01-01"},
        {id: 132, userId: 34, startDate: "2025-01-01"},
      ],
      contributionPeriods: [
        {id: 231, startDate: "2025-01-01", endDate: "2025-12-31", halfYearFee: 10, fullYearFee: 20, alumniFee: 5},
      ],
    })
    await loginAsBoard(page.context())

    await page.goto("/members/manage")
    await expect(page.getByTestId("member-manager-table")).toBeVisible({timeout: 30_000})

    // All users visible before filtering
    await expect(page.getByTestId("member-manager-row-31")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-32")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-33")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-34")).toBeVisible()

    // Filter by name matching only one non-member
    await searchInput(page, "member-manager-search-input").fill("NonTarget")
    await expect(page.getByTestId("member-manager-row-31")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-32")).toHaveCount(0)
    await expect(page.getByTestId("member-manager-row-33")).toHaveCount(0)
    await expect(page.getByTestId("member-manager-row-34")).toHaveCount(0)

    // Filter by first name matching only one member. (Uses the unique "MemberTarget"
    // first name rather than the "member-target" username, which is a substring of
    // non-member "nonmember-target" and would match both in the single table.)
    await searchInput(page, "member-manager-search-input").fill("MemberTarget")
    await expect(page.getByTestId("member-manager-row-33")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-31")).toHaveCount(0)

    // Clear filter — all visible again
    await searchInput(page, "member-manager-search-input").fill("")
    await expect(page.getByTestId("member-manager-row-31")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-34")).toBeVisible()
  })

  test("address manager filters users with and without address by multiple fields", async ({page}) => {
    await installApiMocks(page, {
      users: [
        {
          id: 41,
          fullName: "Addressed Filter Target",
          firstName: "AddressTarget",
          username: "address-target",
          email: "address.target@test.com",
          addressId: 501,
          enabled: true,
          roles: ["MEMBER"],
        },
        {
          id: 42,
          fullName: "Addressed Filter Other",
          firstName: "AddressOther",
          username: "address-other",
          email: "address.other@test.com",
          addressId: 502,
          enabled: true,
          roles: ["MEMBER"],
        },
        {
          id: 43,
          fullName: "No Address Filter Target",
          firstName: "NoAddressTarget",
          username: "no-address-target",
          email: "no.address.target@test.com",
          enabled: true,
          roles: ["MEMBER"],
        },
        {
          id: 44,
          fullName: "No Address Filter Other",
          firstName: "NoAddressOther",
          username: "no-address-other",
          email: "no.address.other@test.com",
          enabled: true,
          roles: ["MEMBER"],
        },
      ],
      addresses: [
        {id: 501, userId: 41, street: "Main", city: "Enschede", zipcode: "1234AB", countryCode: "NL"},
        {id: 502, userId: 42, street: "Main", city: "Enschede", zipcode: "1234AB", countryCode: "NL"},
      ],
    })
    await loginAsBoard(page.context())

    await page.goto("/addresses/manage")
    await expect(page.getByTestId("address-user-list-with-address")).toBeVisible({timeout: 30_000})

    const withAddressCard = await ensureListOpen(
      page,
      "address-user-list-with-address",
      "address-user-list-toggle-with-address",
      "address-user-list-search-with-address",
    )
    await expect(withAddressCard.getByText(exactText("address-target"))).toBeVisible()
    await expect(withAddressCard.getByText(exactText("address-other"))).toBeVisible()

    await searchInput(page, "address-user-list-search-with-address").fill("AddressTarget address.target@test.com")
    await expect(withAddressCard.getByText(exactText("address-target"))).toBeVisible()
    await expect(withAddressCard.getByText(exactText("address-other"))).toHaveCount(0)

    await searchInput(page, "address-user-list-search-with-address").fill("")
    const withoutAddressCard = await ensureListOpen(
      page,
      "address-user-list-without-address",
      "address-user-list-toggle-without-address",
      "address-user-list-search-without-address",
    )
    await expect(withoutAddressCard.getByText(exactText("no-address-target"))).toBeVisible()
    await expect(withoutAddressCard.getByText(exactText("no-address-other"))).toBeVisible()

    await searchInput(page, "address-user-list-search-without-address").fill("NoAddressTarget no.address.target@test.com")
    await expect(withoutAddressCard.getByText(exactText("no-address-target"))).toBeVisible()
    await expect(withoutAddressCard.getByText(exactText("no-address-other"))).toHaveCount(0)
  })

  test("contribution manager filters unpaid and paid users by multiple fields", async ({page}) => {
    await installApiMocks(page, {
      users: [
        {
          id: 11,
          fullName: "Unpaid Filter Target",
          firstName: "UnpaidTarget",
          username: "unpaid-target",
          discord: "unpaid-discord",
          enabled: true,
          roles: ["MEMBER"],
        },
        {
          id: 12,
          fullName: "Unpaid Filter Other",
          firstName: "UnpaidOther",
          username: "unpaid-other",
          discord: "unpaid-other-discord",
          enabled: true,
          roles: ["MEMBER"],
        },
        {
          id: 13,
          fullName: "Paid Filter Target",
          firstName: "PaidTarget",
          username: "paid-target",
          discord: "paid-discord",
          enabled: true,
          roles: ["MEMBER"],
        },
        {
          id: 14,
          fullName: "Paid Filter Other",
          firstName: "PaidOther",
          username: "paid-other",
          discord: "paid-other-discord",
          enabled: true,
          roles: ["MEMBER"],
        },
      ],
      memberships: [
        {id: 101, userId: 11, startDate: "2025-01-01"},
        {id: 102, userId: 12, startDate: "2025-01-01"},
        {id: 103, userId: 13, startDate: "2025-01-01"},
        {id: 104, userId: 14, startDate: "2025-01-01"},
      ],
      contributionPeriods: [
        {id: 201, startDate: "2025-01-01", endDate: "2025-12-31", halfYearFee: 10, fullYearFee: 20, alumniFee: 5},
      ],
      contributions: [
        {id: 301, userId: 13, contributionPeriodId: 201},
        {id: 302, userId: 14, contributionPeriodId: 201},
      ],
    })
    await loginAsBoard(page.context())

    await page.goto("/contributions/manage")
    await expect(page.getByTestId("contribution-user-list-unpaid")).toBeVisible({timeout: 30_000})

    const unpaidCard = await ensureListOpen(
      page,
      "contribution-user-list-unpaid",
      "contribution-user-list-toggle-unpaid",
      "contribution-user-list-search-unpaid",
    )
    await expect(unpaidCard.getByText(exactText("unpaid-target"))).toBeVisible()
    await expect(unpaidCard.getByText(exactText("unpaid-other"))).toBeVisible()

    await searchInput(page, "contribution-user-list-search-unpaid").fill("UnpaidTarget unpaid-discord")
    await expect(unpaidCard.getByText(exactText("unpaid-target"))).toBeVisible()
    await expect(unpaidCard.getByText(exactText("unpaid-other"))).toHaveCount(0)

    await searchInput(page, "contribution-user-list-search-unpaid").fill("")
    const paidCard = await ensureListOpen(
      page,
      "contribution-user-list-paid",
      "contribution-user-list-toggle-paid",
      "contribution-user-list-search-paid",
    )
    await expect(paidCard.getByText(exactText("paid-target"))).toBeVisible()
    await expect(paidCard.getByText(exactText("paid-other"))).toBeVisible()

    await searchInput(page, "contribution-user-list-search-paid").fill("PaidTarget paid-discord")
    await expect(paidCard.getByText(exactText("paid-target"))).toBeVisible()
    await expect(paidCard.getByText(exactText("paid-other"))).toHaveCount(0)
  })

  test("recovery manager filters inactive and active users by multiple fields", async ({page}) => {
    await installApiMocks(page, {
      users: [
        {
          id: 21,
          fullName: "Inactive Filter Target",
          firstName: "InactiveTarget",
          username: "inactive-target",
          email: "inactive.target@test.com",
          enabled: false,
          roles: ["MEMBER"],
        },
        {
          id: 22,
          fullName: "Inactive Filter Other",
          firstName: "InactiveOther",
          username: "inactive-other",
          email: "inactive.other@test.com",
          enabled: false,
          roles: ["MEMBER"],
        },
        {
          id: 23,
          fullName: "Active Filter Target",
          firstName: "ActiveTarget",
          username: "active-target",
          email: "active.target@test.com",
          enabled: true,
          roles: ["MEMBER"],
        },
        {
          id: 24,
          fullName: "Active Filter Other",
          firstName: "ActiveOther",
          username: "active-other",
          email: "active.other@test.com",
          enabled: true,
          roles: ["MEMBER"],
        },
      ],
    })
    await loginAsBoard(page.context())

    await page.goto("/recovery/manage")
    await expect(page.getByTestId("recovery-user-list-inactive")).toBeVisible({timeout: 30_000})

    const inactiveCard = await ensureListOpen(
      page,
      "recovery-user-list-inactive",
      "recovery-user-list-toggle-inactive",
      "recovery-user-list-search-inactive",
    )
    await expect(inactiveCard.getByText(exactText("inactive-target"))).toBeVisible()
    await expect(inactiveCard.getByText(exactText("inactive-other"))).toBeVisible()

    await searchInput(page, "recovery-user-list-search-inactive").fill("InactiveTarget inactive.target@test.com")
    await expect(inactiveCard.getByText(exactText("inactive-target"))).toBeVisible()
    await expect(inactiveCard.getByText(exactText("inactive-other"))).toHaveCount(0)

    await searchInput(page, "recovery-user-list-search-inactive").fill("")
    const activeCard = await ensureListOpen(
      page,
      "recovery-user-list-active",
      "recovery-user-list-toggle-active",
      "recovery-user-list-search-active",
    )
    await expect(activeCard.getByText(exactText("active-target"))).toBeVisible()
    await expect(activeCard.getByText(exactText("active-other"))).toBeVisible()

    await searchInput(page, "recovery-user-list-search-active").fill("ActiveTarget active.target@test.com")
    await expect(activeCard.getByText(exactText("active-target"))).toBeVisible()
    await expect(activeCard.getByText(exactText("active-other"))).toHaveCount(0)
  })
})
