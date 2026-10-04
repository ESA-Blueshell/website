import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin, loginAsBoard} from "./mocks"

const alerts = [
  {key: "job-dead:700", kind: "JOB_DEAD" as const, count: 1, hidden: false, since: "2025-01-01T12:00:11.000Z"},
  {key: "cohort-without-list:5", kind: "COHORT_WITHOUT_LIST" as const, count: 1, hidden: false, subjectId: 5, subjectLabel: "Paid 2026"},
]

test.describe("Alerts in Management", () => {
  test("the count in the sidebar matches the page, and hiding one lowers both", async ({page}) => {
    await installApiMocks(page, {alerts})
    await loginAsAdmin(page.context())
    await page.goto("/management/users")

    await expect(page.getByTestId("management-nav-alerts-count")).toContainText("2")
    await page.getByTestId("management-nav-alerts").click()

    await expect(page.getByTestId("alert-job-dead:700")).toContainText("1 job is dead")
    await expect(page.getByTestId("alert-open-cohort-without-list:5")).toHaveAttribute("href", "/management/platforms/brevo/cohort/5")

    await page.getByTestId("alert-hide-job-dead:700").click()
    await expect(page.getByText("Hidden for you · 1")).toBeVisible()

    await page.getByTestId("alert-open-cohort-without-list:5").click()
    await expect(page.getByTestId("management-nav-alerts-count")).toContainText("1")
  })

  test("a phone carries the count on its Alerts tab", {tag: "@phone"}, async ({page}) => {
    await page.setViewportSize({width: 390, height: 844})
    await installApiMocks(page, {alerts})
    await loginAsBoard(page.context())
    await page.goto("/management/users")

    await expect(page.getByTestId("management-tab-alerts-count")).toHaveText("2")
  })
})
