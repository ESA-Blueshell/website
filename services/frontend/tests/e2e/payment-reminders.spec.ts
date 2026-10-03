import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import {aContributionPeriod, aMembership, aUser} from "./records"

const day = (offset: number) => new Date(Date.now() + offset * 86_400_000).toISOString().slice(0, 10)

const PERIOD = aContributionPeriod({id: 201, startDate: day(-60), endDate: day(240), halfYearCutoffDate: day(90), fullYearFee: 20, halfYearFee: 10, alumniFee: 5})

test.describe("payment reminders", () => {
  test("leaves out members on incasso, previews the real email, and sends only on Send", async ({page}) => {
    await installApiMocks(page, {
      users: [
        aUser({id: 1, fullName: "Emma Dokter", username: "emma", enabled: true, roles: ["MEMBER"]}),
        aUser({id: 2, fullName: "Viktor Petrov", username: "viktor", enabled: true, roles: ["MEMBER"]}),
      ],
      memberships: [
        aMembership({id: 100, userId: 1, memberType: "REGULAR", startDate: "2025-01-01", incasso: false}),
        aMembership({id: 101, userId: 2, memberType: "REGULAR", startDate: "2025-01-01", incasso: true}),
      ],
      contributionPeriods: [PERIOD],
      contributions: [],
    })
    await loginAsBoard(page.context())
    await page.goto(`/management/contributions/${PERIOD.id}`)

    await page.getByTestId("contribution-checkbox-1").click()
    await page.getByTestId("contribution-checkbox-2").click()
    await page.getByTestId("bulk-action-send-payment-reminders").click()

    await expect(page).toHaveURL(/\/management\/contributions\/201\/reminders\?ids=1,2$/)
    await expect(page.getByTestId("payment-reminders-left-out-2")).toContainText("Pays by incasso")
    await page.getByTestId("payment-reminders-next").click()
    await page.getByTestId("payment-reminders-next").click()

    await page.getByTestId("payment-reminders-due-date").locator("input").fill(day(30))
    await page.getByTestId("payment-reminders-preview-1").click()
    await expect(page.getByTestId("email-preview-subject")).toContainText("Please pay your Blueshell contribution")
    await page.keyboard.press("Escape")

    const sent = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith("/contributions/bulk/email/send"))
    await page.getByTestId("payment-reminders-send").click()
    expect((await sent).postDataJSON()).toMatchObject({contributionPeriodId: 201, userIds: [1], paymentDueDate: day(30)})
    await expect(page.getByTestId("payment-reminders-sent")).toBeVisible()
  })
})
