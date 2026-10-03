import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin, loginAsBoard} from "./mocks"
import {anEmail} from "./records"

const emails = [
  anEmail({id: 801, recipientEmail: "lars@example.com", recipientName: "Lars Mulder", subject: "Your contribution for 2026-2027",
    emailType: "email.contribution-reminder", deliveryStatus: "BOUNCED", errorReason: "550 5.1.1 no such mailbox", sentAt: "2026-09-29T09:40:00.000Z",
    attempts: 1, jobExecutionId: 701, createdAt: "2026-09-29T09:39:00.000Z", updatedAt: "2026-09-29T09:41:00.000Z", previewable: true}),
  anEmail({id: 800, recipientEmail: "sam@example.com", recipientName: "Sam", subject: "Reset your password", emailType: "email.recovery",
    deliveryStatus: "QUEUED", sentAt: null, attempts: 0, jobExecutionId: 700, createdAt: "2026-09-29T10:00:00.000Z", previewable: true}),
]

test.describe("Sent", () => {
  test("lists every email from the moment it is queued, and one email is resent from its own page", async ({page}) => {
    await installApiMocks(page, {emails})
    await loginAsBoard(page.context())
    await page.goto("/management/mail/sent")

    await expect(page.getByTestId("sent-email-row-800")).toContainText("Job waiting")
    await expect(page.getByTestId("sent-email-status-801")).toHaveText("Bounced")
    await page.getByTestId("sent-email-open-801").click()

    await expect(page).toHaveURL(/\/management\/mail\/sent\/801$/)
    await expect(page.getByTestId("sent-email-problem")).toContainText("550 5.1.1 no such mailbox")
    await expect(page.getByTestId("sent-email-timeline")).toContainText("Bounced")
    await expect(page.getByTestId("sent-email-job")).toHaveText("Job #701")

    await page.getByTestId("sent-email-resend").click()
    await expect(page.getByTestId("sent-email-resends")).toContainText("Queued")
  })

  test("links an admin to the job that sent an email", async ({page}) => {
    await installApiMocks(page, {emails})
    await loginAsAdmin(page.context())
    await page.goto("/management/mail/sent/801")

    await expect(page.getByTestId("sent-email-job").getByRole("link", {name: "Job #701"})).toHaveAttribute("href", "/management/jobs/701")
  })
})
