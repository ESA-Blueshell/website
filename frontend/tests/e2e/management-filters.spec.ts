import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import type {Locator, Page} from "@playwright/test"

const escapeRegExp = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")

const exactText = (value: string) => new RegExp(`^${escapeRegExp(value)}$`)

const listCard = (page: Page, title: string): Locator =>
  page
    .locator(".v-card")
    .filter({has: page.getByRole("heading", {level: 2, name: exactText(title)})})
    .first()

const searchInput = (card: Locator): Locator =>
  card.getByRole("textbox", {name: /Search for a user/})

const ensureListOpen = async (page: Page, title: string): Promise<Locator> => {
  const card = listCard(page, title)
  const headerToggle = card.locator("[role='button']").first()

  if (await headerToggle.getAttribute("aria-expanded") !== "true") {
    await headerToggle.click()
  }

  await expect(searchInput(card)).toBeVisible()
  return card
}

test.describe("management filters", () => {
  test("member manager filters non-members and members by multiple fields", async ({page}) => {
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
    await expect(page.getByText(/member manager/i).first()).toBeVisible({timeout: 30_000})

    const nonMembersCard = await ensureListOpen(page, "Non-members")
    await expect(nonMembersCard.getByText(exactText("nonmember-target"))).toBeVisible()
    await expect(nonMembersCard.getByText(exactText("nonmember-other"))).toBeVisible()

    await searchInput(nonMembersCard).fill("NonTarget nonmember-discord")
    await expect(nonMembersCard.getByText(exactText("nonmember-target"))).toBeVisible()
    await expect(nonMembersCard.getByText(exactText("nonmember-other"))).toHaveCount(0)

    await searchInput(nonMembersCard).fill("")
    const membersCard = await ensureListOpen(page, "Members")
    await expect(membersCard.getByText(exactText("member-target"))).toBeVisible()
    await expect(membersCard.getByText(exactText("member-other"))).toBeVisible()

    await searchInput(membersCard).fill("MemberTarget member-discord")
    await expect(membersCard.getByText(exactText("member-target"))).toBeVisible()
    await expect(membersCard.getByText(exactText("member-other"))).toHaveCount(0)
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
    await expect(page.getByText(/address manager/i).first()).toBeVisible({timeout: 30_000})

    const withAddressCard = await ensureListOpen(page, "Users with address")
    await expect(withAddressCard.getByText(exactText("address-target"))).toBeVisible()
    await expect(withAddressCard.getByText(exactText("address-other"))).toBeVisible()

    await searchInput(withAddressCard).fill("AddressTarget address.target@test.com")
    await expect(withAddressCard.getByText(exactText("address-target"))).toBeVisible()
    await expect(withAddressCard.getByText(exactText("address-other"))).toHaveCount(0)

    await searchInput(withAddressCard).fill("")
    const withoutAddressCard = await ensureListOpen(page, "Users without address")
    await expect(withoutAddressCard.getByText(exactText("no-address-target"))).toBeVisible()
    await expect(withoutAddressCard.getByText(exactText("no-address-other"))).toBeVisible()

    await searchInput(withoutAddressCard).fill("NoAddressTarget no.address.target@test.com")
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
    await expect(page.getByText(/contribution manager/i).first()).toBeVisible({timeout: 30_000})

    const unpaidCard = await ensureListOpen(page, "Contribution unpaid")
    await expect(unpaidCard.getByText(exactText("unpaid-target"))).toBeVisible()
    await expect(unpaidCard.getByText(exactText("unpaid-other"))).toBeVisible()

    await searchInput(unpaidCard).fill("UnpaidTarget unpaid-discord")
    await expect(unpaidCard.getByText(exactText("unpaid-target"))).toBeVisible()
    await expect(unpaidCard.getByText(exactText("unpaid-other"))).toHaveCount(0)

    await searchInput(unpaidCard).fill("")
    const paidCard = await ensureListOpen(page, "Contribution paid")
    await expect(paidCard.getByText(exactText("paid-target"))).toBeVisible()
    await expect(paidCard.getByText(exactText("paid-other"))).toBeVisible()

    await searchInput(paidCard).fill("PaidTarget paid-discord")
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
    await expect(page.getByText(/recovery manager/i).first()).toBeVisible({timeout: 30_000})

    const inactiveCard = await ensureListOpen(page, "Inactive accounts")
    await expect(inactiveCard.getByText(exactText("inactive-target"))).toBeVisible()
    await expect(inactiveCard.getByText(exactText("inactive-other"))).toBeVisible()

    await searchInput(inactiveCard).fill("InactiveTarget inactive.target@test.com")
    await expect(inactiveCard.getByText(exactText("inactive-target"))).toBeVisible()
    await expect(inactiveCard.getByText(exactText("inactive-other"))).toHaveCount(0)

    await searchInput(inactiveCard).fill("")
    const activeCard = await ensureListOpen(page, "Active accounts")
    await expect(activeCard.getByText(exactText("active-target"))).toBeVisible()
    await expect(activeCard.getByText(exactText("active-other"))).toBeVisible()

    await searchInput(activeCard).fill("ActiveTarget active.target@test.com")
    await expect(activeCard.getByText(exactText("active-target"))).toBeVisible()
    await expect(activeCard.getByText(exactText("active-other"))).toHaveCount(0)
  })
})
