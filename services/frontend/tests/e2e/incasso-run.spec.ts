import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import {aContributionPeriod} from "./records"

const day = (offset: number) => new Date(Date.now() + offset * 86_400_000).toISOString().slice(0, 10)
const typed = (iso: string) => iso.split("-").reverse().join("/")

const PERIOD = aContributionPeriod({id: 201, startDate: day(-60), endDate: day(240), halfYearCutoffDate: day(90), fullYearFee: 25, halfYearFee: 12.5, alumniFee: 5})

test.describe("an incasso run", () => {
  test("collects from members with a mandate, tells them, and waits on Contributions for ING", async ({page}) => {
    await installApiMocks(page, {contributionPeriods: [PERIOD], contributions: []})
    await loginAsBoard(page.context())
    await page.goto(`/management/contributions/${PERIOD.id}`)

    await page.getByTestId("contribution-incasso-run").click()
    await expect(page).toHaveURL(/\/management\/contributions\/201\/incasso$/)
    await expect(page.getByTestId("incasso-run-left-out-203")).toContainText("No bank details recorded")
    await expect(page.getByTestId("incasso-run-left-out-204")).toContainText("Already paid")
    await expect(page.getByTestId("incasso-run-row-201")).toContainText("••12 34")

    await page.getByTestId("incasso-run-next").click()
    await page.getByTestId("incasso-run-next").click()
    await expect(page.getByTestId("incasso-run-renamed")).toContainText("Zoë Bakker as Zoe Bakker")
    await page.getByTestId("incasso-run-date").locator("input").fill(typed(day(30)))
    await page.getByTestId("incasso-run-date").locator("input").press("Tab")

    const started = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith("/contributionPeriods/201/incassoRuns"))
    await page.getByTestId("incasso-run-start").click()
    expect((await started).postDataJSON()).toMatchObject({userIds: [201, 202], collectionDate: day(30)})
    await expect(page.getByTestId("incasso-run-done")).toContainText("2 incasso notifications sent")
    await expect(page).toHaveURL(/\/management\/contributions\/201\/incasso\/70$/)

    await page.getByTestId("incasso-run-done").getByRole("link", {name: "Back to Contributions"}).click()
    await expect(page.getByTestId("contribution-incasso-70")).toContainText("Waiting for upload to ING")
  })
})
