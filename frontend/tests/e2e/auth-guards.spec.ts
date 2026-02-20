import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin, loginAsBoard} from "./mocks"

test.describe("auth guards", () => {
  test("redirects unauthenticated users to login with redirect query", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/account")

    await expect(page).toHaveURL(/\/login\?redirect=.*account/)
    await expect(page.getByText("LOGIN", {exact: true})).toBeVisible()
  })

  test("blocks non-admin users from admin-only route", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management/jobs")

    await expect(page).toHaveURL(/\/$/)
    await expect(page.locator("#blueshell")).toBeVisible()
  })

  test("allows admins to access job manager and retry failed jobs", async ({page}) => {
    await installApiMocks(page, {
      users: [
        {
          id: 1,
          fullName: "Admin User",
          username: "admin",
          enabled: true,
          roles: ["ADMIN", "MEMBER"],
        },
      ],
      jobs: [
        {
          id: 711,
          jobType: "SYNC_DISCORD",
          status: "FAILED",
          attempts: 2,
          payload: "{\"scope\":\"members\"}",
          errorType: "RuntimeException",
          errorReason: "Temporary failure",
          queuedAt: "2025-01-01T12:00:00.000Z",
          startedAt: "2025-01-01T12:00:10.000Z",
          finishedAt: "2025-01-01T12:00:11.000Z",
        },
      ],
    })
    await loginAsAdmin(page.context())

    await page.goto("/management/jobs")

    await expect(page).toHaveURL(/\/management\/jobs/)
    await expect(page.getByText("JOB MANAGER", {exact: true})).toBeVisible()
    await expect(page.getByTestId("job-manager-table")).toBeVisible()
    await expect(page.getByText("FAILED").first()).toBeVisible()

    await page.getByRole("button", {name: "Retry"}).click()
    await expect(page.getByText("RUNNING").first()).toBeVisible()
  })
})
