import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin, loginAsBoard} from "./mocks"

test.describe("job manager stats panel", () => {
  test("stats panel shows correct total and status counts", async ({page}) => {
    await installApiMocks(page, {
      jobs: [
        {id: 1, jobType: "email.send", status: "SUCCESS", attempts: 1},
        {id: 2, jobType: "calendar.sync", status: "SUCCESS", attempts: 1},
        {id: 3, jobType: "contact.sync", status: "FAILED", attempts: 3},
        {id: 4, jobType: "email.send", status: "DEAD", attempts: 5},
      ],
    })
    await loginAsAdmin(page.context())
    await page.goto("/management/jobs")

    await expect(page.getByTestId("job-stats-total")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId("job-stats-total")).toContainText("4")
    await expect(page.getByTestId("job-stats-success")).toContainText("2")
    await expect(page.getByTestId("job-stats-failed")).toContainText("1")
    await expect(page.getByTestId("job-stats-dead")).toContainText("1")
  })

  test("stats panel shows success rate percentage", async ({page}) => {
    await installApiMocks(page, {
      jobs: [
        {id: 1, jobType: "email.send", status: "SUCCESS", attempts: 1},
        {id: 2, jobType: "calendar.sync", status: "SUCCESS", attempts: 1},
        {id: 3, jobType: "contact.sync", status: "SUCCESS", attempts: 1},
        {id: 4, jobType: "email.send", status: "FAILED", attempts: 3},
      ],
    })
    await loginAsAdmin(page.context())
    await page.goto("/management/jobs")

    await expect(page.getByTestId("job-stats-success")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId("job-stats-success")).toContainText("75%")
  })

  test("stats panel shows runtime section", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())
    await page.goto("/management/jobs")

    await expect(page.getByTestId("job-stats-runtime")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId("job-stats-runtime")).toContainText("Since last startup")
  })

  test("stats panel is not visible to non-admin users", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management/jobs")
    await expect(page).toHaveURL(/\/$/)
  })
})
