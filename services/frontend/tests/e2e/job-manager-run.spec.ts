import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"
import type {EnqueueJobRequest} from "@/services/api"

test.describe("Run a job on the Jobs page", () => {
  test("an admin opens Run a job, picks a user via the UserPicker and queues a job", async ({page}) => {
    await installApiMocks(page, {jobs: []})
    await loginAsAdmin(page.context())
    await page.goto("/management/jobs")

    await page.getByTestId("job-run-toggle").click()

    const form = page.getByTestId("job-run-form")
    await expect(form).toBeVisible()

    // Pick a job type from the generated catalog.
    await page.getByTestId("job-run-type").click()
    await page.getByRole("option", {name: "Sync contact", exact: true}).click()

    // A `userId: Long` payload field renders as a UserPicker
    // (v-autocomplete backed by /users). Click the input to open the
    // dropdown, then pick the mocked user "Emma Dokter" (id=1).
    const userIdField = page.getByTestId("job-run-field-userId").locator("input").first()
    await expect(userIdField).toBeVisible()
    await userIdField.click()
    await page.getByRole("option", {name: /Emma Dokter/}).click()

    const enqueueResponse = page.waitForResponse(
      (response) =>
        response.url().includes("/management/jobs/enqueue") && response.request().method() === "POST",
    )
    await page.getByTestId("job-run-submit").click()
    const response = await enqueueResponse
    expect(response.status()).toBe(200)

    const body = response.request().postDataJSON() as EnqueueJobRequest
    expect(body.jobType).toBe("contact.sync")
    expect(body.payload).toEqual({userId: 1})

    await expect(page.getByTestId("job-run-queued")).toHaveText("Sync contact is queued.")
  })

  test("Queue the job stays disabled until required arguments are filled", async ({page}) => {
    await installApiMocks(page, {jobs: []})
    await loginAsAdmin(page.context())
    await page.goto("/management/jobs")

    await page.getByTestId("job-run-toggle").click()
    await page.getByTestId("job-run-type").click()
    await page.getByRole("option", {name: "Sync contact", exact: true}).click()

    await expect(page.getByTestId("job-run-submit")).toBeDisabled()

    const userIdField = page.getByTestId("job-run-field-userId").locator("input").first()
    await expect(userIdField).toBeVisible()
    await userIdField.click()
    await page.getByRole("option", {name: /Emma Dokter/}).click()
    await expect(page.getByTestId("job-run-submit")).toBeEnabled()
  })
})

test.describe("the Jobs filters", () => {
  test("Status and Kind narrow the list alongside the search", async ({page}) => {
    await installApiMocks(page, {jobs: []})
    await loginAsAdmin(page.context())
    await page.goto("/management/jobs")

    const filtered = page.waitForRequest((request) => request.url().includes("status=FAILED") && request.url().includes("category=calendar"))
    await page.getByTestId("job-filter-status-search").click()
    await page.getByTestId("job-filter-status-FAILED").click()
    await page.getByTestId("job-filter-kind-search").click()
    await page.getByTestId("job-filter-kind-calendar").click()
    await filtered

    const searched = page.waitForRequest((request) => request.url().includes("search=sitecie") && request.url().includes("status=FAILED"))
    await page.getByTestId("job-filter-search").fill("sitecie")
    await searched

    await page.getByTestId("job-filters-clear").click()
    await expect(page.getByTestId("job-filters-clear")).toHaveCount(0)
  })
})

test.describe("one job", () => {
  test("shows its failure and sends Run again to Run a job, filled in", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())
    await page.goto("/management/jobs/700")

    await expect(page.getByTestId("job-detail-failure")).toContainText("Temporary failure")
    await expect(page.getByTestId("management-nav-jobs")).toHaveAttribute("aria-current", "page")

    await page.getByTestId("job-detail-run-again").click()
    await expect(page).toHaveURL(/\/management\/jobs\?again=700$/)
    await expect(page.getByTestId("job-run-form")).toBeVisible()
  })
})
