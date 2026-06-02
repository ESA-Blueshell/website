import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

test.describe("cohort dashboard", () => {
  test("lists cohorts grouped by system and lets the admin re-evaluate every user", async ({page}) => {
    await installApiMocks(page, {jobs: []})
    await loginAsAdmin(page.context())
    await page.goto("/management/cohorts")

    await expect(page.getByTestId("cohort-list")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId("cohort-row-1")).toContainText("Members")
    await expect(page.getByTestId("cohort-row-2")).toContainText("Contribution Paid 25-26")

    const enqueueResponse = page.waitForResponse(
      (response) =>
        response.url().includes("/management/jobs/enqueue") && response.request().method() === "POST",
    )
    await page.getByTestId("cohort-action-reconcile-users").click()
    const response = await enqueueResponse
    expect(response.status()).toBe(200)
    const body = response.request().postDataJSON() as {jobType: string}
    expect(body.jobType).toBe("cohort.reconcile-all-users")

    await expect(page.getByTestId("cohort-success")).toBeVisible()
  })

  test("opens a cohort detail showing members and rules", async ({page}) => {
    await installApiMocks(page, {jobs: []})
    await loginAsAdmin(page.context())
    await page.goto("/management/cohorts/2")

    await expect(page.getByTestId("cohort-member-list")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId("cohort-member-1")).toContainText("Emma Dokter")
    await expect(page.getByTestId("cohort-rule-9")).toContainText("CONTRIBUTION_PAID")
  })
})
