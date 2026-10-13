import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin, loginAsBoard} from "./mocks"

// Read afresh by each test: the stand-in marks what a test hides on the records it was given.
const alerts = () => [
  {key: "job-dead", kind: "JOB_DEAD" as const, count: 1, hidden: false, since: "2025-01-01T12:00:11.000Z"},
  {key: "exception-open", kind: "EXCEPTION_OPEN" as const, count: 2, hidden: false, since: "2025-01-02T12:00:00.000Z"},
]

test.describe("Alerts in Management", () => {
  test("the count in the sidebar matches the page, and hiding one lowers both", async ({page}) => {
    await installApiMocks(page, {alerts: alerts()})
    await loginAsAdmin(page.context())
    await page.goto("/management/jobs")

    await expect(page.getByTestId("management-nav-alerts-count")).toContainText("2")
    await page.getByTestId("management-nav-alerts").click()

    await expect(page.getByTestId("alert-job-dead")).toContainText("1 job is dead")
    await expect(page.getByTestId("alert-open-exception-open")).toHaveAttribute("href", "/management/exceptions")

    await page.getByTestId("alert-hide-job-dead").click()
    await expect(page.getByText("Hidden · 1")).toBeVisible()

    await page.getByTestId("alert-open-exception-open").click()
    await expect(page).toHaveURL(/\/management\/exceptions$/)
    await expect(page.getByTestId("management-nav-alerts-count")).toContainText("1")
  })

  test("a phone carries the count on its Alerts tab", {tag: "@phone"}, async ({page}) => {
    await page.setViewportSize({width: 390, height: 844})
    await installApiMocks(page, {alerts: alerts()})
    await loginAsBoard(page.context())
    await page.goto("/management/more")

    await expect(page.getByTestId("management-tab-alerts-count")).toContainText("2")
  })
})
