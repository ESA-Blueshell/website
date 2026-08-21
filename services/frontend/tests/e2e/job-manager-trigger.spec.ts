import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

test.describe("job manager trigger modal", () => {
  test("admin opens the modal, picks a user via the UserPicker and triggers a job", async ({page}) => {
    await installApiMocks(page, {jobs: []})
    await loginAsAdmin(page.context())
    await page.goto("/management/jobs")

    await expect(page.getByTestId("job-manager-trigger-btn")).toBeVisible()
    await page.getByTestId("job-manager-trigger-btn").click()

    const dialog = page.getByTestId("job-trigger-dialog")
    await expect(dialog).toBeVisible()

    // Pick a job type from the generated catalog.
    await page.getByTestId("job-trigger-type").click()
    await page.getByRole("option", {name: "Sync contact", exact: true}).click()

    // A `userId: Long` payload field renders as a UserPicker
    // (v-autocomplete backed by /users). Click the input to open the
    // dropdown, then pick the mocked user "Emma Dokter" (id=1).
    const userIdField = page.getByTestId("job-trigger-field-userId").locator("input").first()
    await expect(userIdField).toBeVisible()
    await userIdField.click()
    await page.getByRole("option", {name: /Emma Dokter/}).click()

    const enqueueResponse = page.waitForResponse(
      (response) =>
        response.url().includes("/management/jobs/enqueue") && response.request().method() === "POST",
    )
    await page.getByTestId("job-trigger-submit").click()
    const response = await enqueueResponse
    expect(response.status()).toBe(200)

    const body = response.request().postDataJSON() as {jobType: string; payload: Record<string, unknown>}
    expect(body.jobType).toBe("contact.sync")
    expect(body.payload).toEqual({userId: 1})

    await expect(dialog).toBeHidden()
  })

  test("submit stays disabled until required arguments are filled", async ({page}) => {
    await installApiMocks(page, {jobs: []})
    await loginAsAdmin(page.context())
    await page.goto("/management/jobs")

    await page.getByTestId("job-manager-trigger-btn").click()
    await page.getByTestId("job-trigger-type").click()
    await page.getByRole("option", {name: "Sync contact", exact: true}).click()

    await expect(page.getByTestId("job-trigger-submit")).toBeDisabled()

    const userIdField = page.getByTestId("job-trigger-field-userId").locator("input").first()
    await expect(userIdField).toBeVisible()
    await userIdField.click()
    await page.getByRole("option", {name: /Emma Dokter/}).click()
    await expect(page.getByTestId("job-trigger-submit")).toBeEnabled()
  })
})
