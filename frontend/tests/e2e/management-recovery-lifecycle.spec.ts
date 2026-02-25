import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import type {Locator} from "@playwright/test"

const ensureExpanded = async (toggle: Locator) => {
  if (await toggle.getAttribute("aria-expanded") !== "true") {
    await toggle.click()
  }
}

test.describe("management recovery lifecycle", () => {
  test("deletes user from member manager and restores in recovery manager", async ({page}) => {
    const targetId = 711
    const targetUsername = "lifecycle-target"

    await installApiMocks(page, {
      users: [
        {
          id: targetId,
          fullName: "Lifecycle Target",
          firstName: "Lifecycle",
          lastName: "Target",
          initials: "LT",
          username: targetUsername,
          email: "lifecycle.target@test.com",
          discord: "lifecycle-target",
          phoneNumber: "+31612345678",
          newsletter: true,
          enabled: true,
          roles: ["USER"],
          version: 0,
          createdAt: "2025-01-01T00:00:00.000Z",
          updatedAt: "2025-01-01T00:00:00.000Z",
        },
      ],
      deletedUsers: [],
    })
    await loginAsBoard(page.context())

    await page.goto("/members/manage")
    await expect(page.getByTestId("member-user-list-non-members")).toBeVisible({timeout: 30_000})

    const nonMembersToggle = page.getByTestId("member-user-list-toggle-non-members").first()
    await ensureExpanded(nonMembersToggle)

    const nonMembersCard = page.getByTestId("member-user-list-non-members").first()
    await expect(nonMembersCard.getByTestId(`member-user-row-${targetId}`)).toBeVisible()

    await nonMembersCard.getByTestId(`member-user-delete-btn-${targetId}`).click()
    await expect(page.getByTestId("deletion-confirmation-dialog")).toBeVisible()
    await page.getByTestId("deletion-confirmation-confirm-btn").click()

    await expect(nonMembersCard.getByTestId(`member-user-row-${targetId}`)).toHaveCount(0)

    await page.goto("/recovery/manage")
    await expect(page.getByTestId("recovery-user-list-deleted")).toBeVisible({timeout: 30_000})

    const deletedToggle = page.getByTestId("recovery-user-list-toggle-deleted").first()
    await ensureExpanded(deletedToggle)

    const deletedCard = page.getByTestId("recovery-user-list-deleted").first()
    await deletedCard.getByTestId("recovery-user-list-search-deleted").locator("input").first().fill(targetUsername)
    await expect(deletedCard.getByTestId(`recovery-user-row-${targetId}`)).toBeVisible()

    await deletedCard.getByTestId(`recovery-user-action-btn-restore-${targetId}`).click()
    await expect(deletedCard.getByTestId(`recovery-user-row-${targetId}`)).toHaveCount(0)

    const activeToggle = page.getByTestId("recovery-user-list-toggle-active").first()
    await ensureExpanded(activeToggle)

    const activeCard = page.getByTestId("recovery-user-list-active").first()
    await activeCard.getByTestId("recovery-user-list-search-active").locator("input").first().fill(targetUsername)
    await expect(activeCard.getByTestId(`recovery-user-row-${targetId}`)).toBeVisible()
  })
})
