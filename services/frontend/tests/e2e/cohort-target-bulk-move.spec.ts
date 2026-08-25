import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

const PAGE = "/management/cohorts/targets"

test.describe("moving cohort targets in bulk", () => {
  test.beforeEach(async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())
  })

  test("offers no bulk move until something is selected", async ({page}) => {
    await page.goto(PAGE)

    await expect(page.getByTestId("cohort-targets-selection-bar")).toBeVisible()
    await expect(page.getByTestId("cohort-targets-move-selected")).toBeDisabled()
  })

  test("selects every target the search shows, not every target there is", async ({page}) => {
    await page.goto(PAGE)
    // The testid is on the Vuetify field wrapper; the input is inside it.
    await page.getByTestId("cohort-targets-search").locator("input").fill("Committees")
    // The checkbox's own input: the testid is on the wrapper, which also spans its label.
    await page.getByTestId("cohort-targets-select-all").locator("input").click()

    // Two lists sit in Committees; the other two are filed elsewhere.
    await expect(page.getByTestId("cohort-targets-selected-count")).toContainText("2 selected")
  })

  test("files the selected targets under one folder", async ({page}) => {
    await page.goto(PAGE)
    await page.getByTestId("cohort-target-select-33").locator("input").click()
    await page.getByTestId("cohort-target-select-34").locator("input").click()
    await page.getByTestId("cohort-targets-move-selected").click()

    await expect(page.getByTestId("cohort-target-move-dialog")).toBeVisible()
    await page.getByTestId("cohort-target-move-folder").click()
    await page.getByRole("option", {name: "Archive"}).click()
    await page.getByTestId("cohort-target-move-confirm").click()

    // The rows follow the api's answer, so both land in the folder it reported.
    await expect(page.getByTestId("cohort-target-folder-Archive")).toBeVisible()
    await expect(page.getByTestId("cohort-targets-selected-count")).toBeHidden()
  })

  test("keeps the selection and names the reason when the api refuses it whole", async ({page}) => {
    await page.goto(PAGE)
    // Target 99 is not in the catalogue the page rendered; the search finds nothing, so the
    // refusal is driven through a target the mock treats as gone.
    await page.getByTestId("cohort-target-select-33").locator("input").click()
    await page.route("**/management/cohort-targets/BREVO/folder", async (route) => {
      await route.fulfill({
        status: 409,
        contentType: "application/problem+json",
        body: JSON.stringify({
          status: 409,
          detail: "The selection no longer matches the current data.",
          errors: [{
            objectName: "BulkMoveTargetsRequest",
            field: "externalIds",
            code: "UnknownTargetIds",
            message: "1 of the selected targets no longer exist in BREVO.",
            refs: ["33"],
          }],
        }),
      })
    })

    await page.getByTestId("cohort-targets-move-selected").click()
    await page.getByTestId("cohort-target-move-folder").click()
    await page.getByRole("option", {name: "Archive"}).click()
    await page.getByTestId("cohort-target-move-confirm").click()

    const refusal = page.getByTestId("cohort-target-move-rejection")
    await expect(refusal).toContainText("no longer exist")
    // Nothing was written, so the dialog stays open and the tick stays put.
    await expect(page.getByTestId("cohort-target-move-dialog")).toBeVisible()
    await expect(page.getByTestId("cohort-target-move-reload")).toBeVisible()
  })

  test("still moves one target on its own", async ({page}) => {
    await page.goto(PAGE)
    await page.getByTestId("cohort-target-move-33").click()

    await expect(page.getByTestId("cohort-target-move-dialog")).toContainText("Move Web Cmte")
  })
})
