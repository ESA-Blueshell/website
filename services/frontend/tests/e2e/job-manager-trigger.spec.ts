import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

test.describe("job manager trigger modal", () => {
  test("admin opens the modal, fills arguments and triggers a job", async ({page}) => {
    await installApiMocks(page, {jobs: []})
    await loginAsAdmin(page.context())
    await page.goto("/management/jobs")

    await expect(page.getByTestId("job-manager-trigger-btn")).toBeVisible({timeout: 30_000})
    await page.getByTestId("job-manager-trigger-btn").click()

    const dialog = page.getByTestId("job-trigger-dialog")
    await expect(dialog).toBeVisible()

    // Pick a job type from the generated catalog.
    await page.getByTestId("job-trigger-type").click()
    await page.getByRole("option", {name: "Contact Sync"}).click()

    // The argument input is rendered from the type's payload fields.
    const userIdField = page.getByTestId("job-trigger-field-userId").locator("input")
    await expect(userIdField).toBeVisible()
    await userIdField.fill("42")

    const enqueueResponse = page.waitForResponse(
      (response) =>
        response.url().includes("/management/jobs/enqueue") && response.request().method() === "POST",
    )
    await page.getByTestId("job-trigger-submit").click()
    const response = await enqueueResponse
    expect(response.status()).toBe(200)

    const body = response.request().postDataJSON() as {jobType: string; payload: Record<string, unknown>}
    expect(body.jobType).toBe("contact.sync")
    expect(body.payload).toEqual({userId: 42})

    await expect(dialog).toBeHidden()
  })

  test("submit stays disabled until required arguments are filled", async ({page}) => {
    await installApiMocks(page, {jobs: []})
    await loginAsAdmin(page.context())
    await page.goto("/management/jobs")

    await page.getByTestId("job-manager-trigger-btn").click()
    await page.getByTestId("job-trigger-type").click()
    await page.getByRole("option", {name: "Contact Sync"}).click()

    await expect(page.getByTestId("job-trigger-submit")).toBeDisabled()

    await page.getByTestId("job-trigger-field-userId").locator("input").fill("7")
    await expect(page.getByTestId("job-trigger-submit")).toBeEnabled()
  })
})
