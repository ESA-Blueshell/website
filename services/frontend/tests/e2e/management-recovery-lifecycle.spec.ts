import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import {aUser} from "./records"

test.describe("management recovery lifecycle", () => {
  test("deletes user from member manager and restores in recovery manager", async ({page}) => {
    const targetId = 711
    const targetUsername = "lifecycle-target"

    await installApiMocks(page, {
      users: [
        aUser({
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
          roles: ["GUEST"],
          version: 0,
          createdAt: "2025-01-01T00:00:00.000Z",
          updatedAt: "2025-01-01T00:00:00.000Z",
        }),
      ],
      deletedUsers: [],
    })
    await loginAsBoard(page.context())

    // The member manager renders its unified table only at the lg breakpoint and
    // up; below that it switches to a mobile card list with its own test ids. This
    // test exercises the desktop table, so it pins a desktop viewport.
    await page.setViewportSize({width: 1440, height: 900})
    await page.goto("/management/users")
    await expect(page.getByTestId("member-manager-table")).toBeVisible()

    // The user row should be visible in the unified table
    await expect(page.getByTestId(`member-manager-row-${targetId}`)).toBeVisible()

    // Delete sits in the row's actions menu.
    await page.getByTestId(`member-manager-actions-${targetId}`).click()
    await page.getByTestId(`member-manager-delete-btn-${targetId}`).click()
    await expect(page.getByTestId("deletion-confirmation-dialog")).toBeVisible()
    await page.getByTestId("deletion-confirmation-confirm-btn").click()

    // Row should be removed from the table
    await expect(page.getByTestId(`member-manager-row-${targetId}`)).toHaveCount(0)

    // Account recovery restores the deleted user
    await page.goto("/management/recovery")
    await page.getByTestId("recovery-search").fill(targetUsername)
    await expect(page.getByTestId(`recovery-state-${targetId}`)).toContainText("Deleted")

    await page.getByTestId(`recovery-user-action-btn-restore-${targetId}`).click()
    await expect(page.getByTestId(`recovery-state-${targetId}`)).toHaveText("Active")
  })
})
