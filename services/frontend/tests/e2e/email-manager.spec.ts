import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin, loginAsBoard} from "./mocks"

const BASE_EMAILS = [
  {
    id: 801,
    recipientEmail: "alice@example.com",
    recipientName: "Alice Example",
    subject: "Welcome to Blueshell",
    emailType: "email.activation",
    deliveryStatus: "DELIVERED",
    messageId: "<msg-801@blueshell.utwente.nl>",
    sentAt: "2025-01-01T10:00:00.000Z",
    deliveredAt: "2025-01-01T10:00:30.000Z",
    openedAt: null,
    attempts: 1,
    jobExecutionId: 700,
    createdAt: "2025-01-01T09:59:00.000Z",
  },
  {
    id: 802,
    recipientEmail: "bob@example.com",
    recipientName: "Bob Example",
    subject: "Reset Your Blueshell Account Password",
    emailType: "email.recovery",
    deliveryStatus: "FAILED",
    messageId: null,
    sentAt: null,
    deliveredAt: null,
    openedAt: null,
    errorType: "SmtpConnectException",
    errorReason: "Connection refused",
    attempts: 3,
    jobExecutionId: 701,
    createdAt: "2025-01-01T11:00:00.000Z",
  },
  {
    id: 803,
    recipientEmail: "carol@example.com",
    recipientName: "Carol Example",
    subject: "Contribution Payment Reminder",
    emailType: "email.contribution",
    deliveryStatus: "OPENED",
    messageId: "<msg-803@blueshell.utwente.nl>",
    sentAt: "2025-01-01T12:00:00.000Z",
    deliveredAt: "2025-01-01T12:00:30.000Z",
    openedAt: "2025-01-01T13:00:00.000Z",
    attempts: 1,
    jobExecutionId: null,
    createdAt: "2025-01-01T11:59:00.000Z",
  },
  {
    id: 804,
    recipientEmail: "dave@example.com",
    recipientName: "Dave Example",
    subject: "Event Registration Confirmed - Summer Tournament",
    emailType: "email.event",
    deliveryStatus: "SENT",
    messageId: "<msg-804@blueshell.utwente.nl>",
    sentAt: "2025-01-01T14:00:00.000Z",
    deliveredAt: null,
    openedAt: null,
    attempts: 1,
    jobExecutionId: null,
    createdAt: "2025-01-01T13:59:00.000Z",
  },
]

test.describe("email manager — access control", () => {
  test("non-admin is redirected to home", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsBoard(page.context())

    await page.goto("/management/emails")
    await expect(page).toHaveURL(/\/$/, {timeout: 10_000})
  })

  test("admin can access the email manager", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())

    await page.goto("/management/emails")
    await expect(page.getByTestId("email-manager-table")).toBeVisible({timeout: 30_000})
  })
})

test.describe("email manager — stats panel", () => {
  test("stats panel shows correct counts per status", async ({page}) => {
    await installApiMocks(page, {
      emails: [
        {...BASE_EMAILS[0], id: 810, deliveryStatus: "DELIVERED"},
        {...BASE_EMAILS[1], id: 811, deliveryStatus: "FAILED"},
        {...BASE_EMAILS[2], id: 812, deliveryStatus: "OPENED"},
        {...BASE_EMAILS[3], id: 813, deliveryStatus: "SENT"},
        {...BASE_EMAILS[0], id: 814, deliveryStatus: "DELIVERED"},
      ],
    })
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId("email-stats-total")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId("email-stats-total")).toContainText("5")
    await expect(page.getByTestId("email-stats-sent")).toContainText("1")
    await expect(page.getByTestId("email-stats-delivered")).toContainText("2")
    await expect(page.getByTestId("email-stats-opened")).toContainText("1")
    await expect(page.getByTestId("email-stats-failed")).toContainText("1")
  })

  test("stats panel shows delivery rate percentage", async ({page}) => {
    await installApiMocks(page, {
      emails: [
        {...BASE_EMAILS[0], id: 820, deliveryStatus: "DELIVERED"},
        {...BASE_EMAILS[0], id: 821, deliveryStatus: "DELIVERED"},
        {...BASE_EMAILS[1], id: 822, deliveryStatus: "FAILED"},
        {...BASE_EMAILS[3], id: 823, deliveryStatus: "SENT"},
      ],
    })
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    // 2 delivered out of 4 total = 50%
    await expect(page.getByTestId("email-stats-delivered")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId("email-stats-delivered")).toContainText("50%")
  })

  test("stats panel shows open rate percentage", async ({page}) => {
    await installApiMocks(page, {
      emails: [
        {...BASE_EMAILS[2], id: 830, deliveryStatus: "OPENED"},
        {...BASE_EMAILS[0], id: 831, deliveryStatus: "DELIVERED"},
        {...BASE_EMAILS[0], id: 832, deliveryStatus: "DELIVERED"},
        {...BASE_EMAILS[1], id: 833, deliveryStatus: "FAILED"},
      ],
    })
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    // 1 opened out of 4 total = 25%
    await expect(page.getByTestId("email-stats-opened")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId("email-stats-opened")).toContainText("25%")
  })
})

test.describe("email manager — email list", () => {
  test("shows all emails in list", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId("email-manager-table")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId(`email-row-${BASE_EMAILS[0].id}`)).toBeVisible()
    await expect(page.getByTestId(`email-row-${BASE_EMAILS[1].id}`)).toBeVisible()
    await expect(page.getByTestId(`email-row-${BASE_EMAILS[2].id}`)).toBeVisible()
  })

  test("shows 'No emails found' when list is empty", async ({page}) => {
    await installApiMocks(page, {emails: []})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByText("No emails found.")).toBeVisible({timeout: 30_000})
  })

  test("displays status chip for each email row", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId("email-manager-table")).toBeVisible({timeout: 30_000})
    const deliveredRow = page.getByTestId(`email-row-${BASE_EMAILS[0].id}`)
    await expect(deliveredRow).toContainText("DELIVERED")

    const failedRow = page.getByTestId(`email-row-${BASE_EMAILS[1].id}`)
    await expect(failedRow).toContainText("FAILED")
  })
})

test.describe("email manager — filtering", () => {
  test("filter by delivery status shows only matching emails", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId("email-manager-table")).toBeVisible({timeout: 30_000})

    // Select FAILED status filter
    await page.getByTestId("email-filter-status").click()
    await page.getByTestId("email-filter-status-option-failed").click()

    await expect(page.getByTestId(`email-row-${BASE_EMAILS[1].id}`)).toBeVisible()
    await expect(page.getByTestId(`email-row-${BASE_EMAILS[0].id}`)).not.toBeVisible()
    await expect(page.getByTestId(`email-row-${BASE_EMAILS[2].id}`)).not.toBeVisible()
  })

  test("search filters emails by recipient email", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId("email-manager-table")).toBeVisible({timeout: 30_000})

    const searchInput = page.getByTestId("email-filter-search").locator("input").first()
    await searchInput.fill("alice")

    await expect(page.getByTestId(`email-row-${BASE_EMAILS[0].id}`)).toBeVisible()
    await expect(page.getByTestId(`email-row-${BASE_EMAILS[1].id}`)).not.toBeVisible()
  })

  test("search filters emails by subject", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId("email-manager-table")).toBeVisible({timeout: 30_000})

    const searchInput = page.getByTestId("email-filter-search").locator("input").first()
    await searchInput.fill("Contribution")

    await expect(page.getByTestId(`email-row-${BASE_EMAILS[2].id}`)).toBeVisible()
    await expect(page.getByTestId(`email-row-${BASE_EMAILS[0].id}`)).not.toBeVisible()
  })

  test("refresh button reloads the list", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId("email-manager-refresh-btn")).toBeVisible({timeout: 30_000})
    await page.getByTestId("email-manager-refresh-btn").click()

    // After refresh the list should still be visible
    await expect(page.getByTestId("email-manager-table")).toBeVisible()
  })
})

test.describe("email manager — expanded details", () => {
  test("clicking a row expands email details", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId(`email-row-${BASE_EMAILS[0].id}`)).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId(`email-detail-${BASE_EMAILS[0].id}`)).not.toBeVisible()

    await page.getByTestId(`email-row-${BASE_EMAILS[0].id}`).click()

    await expect(page.getByTestId(`email-detail-${BASE_EMAILS[0].id}`)).toBeVisible()
  })

  test("expanded details show recipient name and email", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId(`email-row-${BASE_EMAILS[0].id}`)).toBeVisible({timeout: 30_000})
    await page.getByTestId(`email-row-${BASE_EMAILS[0].id}`).click()

    const detail = page.getByTestId(`email-detail-${BASE_EMAILS[0].id}`)
    await expect(detail).toContainText("Alice Example")
    await expect(detail).toContainText("alice@example.com")
  })

  test("expanded details show error information for failed emails", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId(`email-row-${BASE_EMAILS[1].id}`)).toBeVisible({timeout: 30_000})
    await page.getByTestId(`email-row-${BASE_EMAILS[1].id}`).click()

    const detail = page.getByTestId(`email-detail-${BASE_EMAILS[1].id}`)
    await expect(detail).toContainText("SmtpConnectException")
    await expect(detail).toContainText("Connection refused")
  })

  test("clicking expanded row collapses it", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId(`email-row-${BASE_EMAILS[0].id}`)).toBeVisible({timeout: 30_000})
    await page.getByTestId(`email-row-${BASE_EMAILS[0].id}`).click()
    await expect(page.getByTestId(`email-detail-${BASE_EMAILS[0].id}`)).toBeVisible()

    await page.getByTestId(`email-row-${BASE_EMAILS[0].id}`).click()
    await expect(page.getByTestId(`email-detail-${BASE_EMAILS[0].id}`)).not.toBeVisible()
  })
})

test.describe("email manager — retry", () => {
  test("retry button is only visible for FAILED emails with a linked job", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId("email-manager-table")).toBeVisible({timeout: 30_000})

    // FAILED email with jobExecutionId — retry button should appear in the append slot
    const failedRow = page.getByTestId(`email-row-${BASE_EMAILS[1].id}`)
    await expect(failedRow).toBeVisible()
    await expect(page.getByTestId(`email-retry-btn-${BASE_EMAILS[1].id}`)).toBeVisible()

    // DELIVERED email — no retry button
    await expect(page.getByTestId(`email-retry-btn-${BASE_EMAILS[0].id}`)).not.toBeVisible()

    // OPENED email (no linked job) — no retry button
    await expect(page.getByTestId(`email-retry-btn-${BASE_EMAILS[2].id}`)).not.toBeVisible()
  })

  test("clicking retry updates the email status", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId(`email-retry-btn-${BASE_EMAILS[1].id}`)).toBeVisible({timeout: 30_000})
    await page.getByTestId(`email-retry-btn-${BASE_EMAILS[1].id}`).click()

    // After retry the mock returns status SENT, list should refresh and row shows SENT
    const failedRow = page.getByTestId(`email-row-${BASE_EMAILS[1].id}`)
    await expect(failedRow).toContainText("SENT", {timeout: 10_000})
  })
})

test.describe("email manager — pagination", () => {
  test("pagination is visible when there are emails", async ({page}) => {
    await installApiMocks(page, {emails: BASE_EMAILS})
    await loginAsAdmin(page.context())
    await page.goto("/management/emails")

    await expect(page.getByTestId("email-manager-table")).toBeVisible({timeout: 30_000})
    await expect(page.getByTestId("email-manager-pagination")).toBeVisible()
  })
})
