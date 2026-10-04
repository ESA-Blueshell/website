import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("the Inbox", () => {
  test("lists replies to the site's emails and mail to other addresses, from the sidebar", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/management/mail/sent")

    await page.getByRole("link", {name: "Inbox"}).click()
    await expect(page).toHaveURL(/\/management\/mail\/inbox$/)
    await expect(page.getByTestId("inbox-new")).toContainText("2")
    await expect(page.getByTestId("inbox-follows-1")).toHaveText("Answers contribution reminder")
    await expect(page.getByTestId("inbox-follows-2")).toHaveText("To partners@")
  })

  test("opens a message on its conversation and sends a reply in its thread", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/management/mail/inbox")

    await page.getByTestId("inbox-open-1").click()
    await expect(page).toHaveURL(/\/management\/mail\/inbox\/1$/)
    await expect(page.getByTestId("inbox-message-item-0")).toContainText("The site · Your contribution")
    await expect(page.getByTestId("inbox-message-item-1")).toContainText("I already paid.")
    await expect(page.getByTestId("inbox-message-earlier")).toContainText("Welcome to Blueshell")

    await page.getByTestId("inbox-reply-editor").locator(".cm-content").fill("Thanks, it is marked as paid.")
    const sent = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith("/mail/inbox/1/reply"))
    await page.getByTestId("inbox-send-reply").click()
    expect((await sent).postDataJSON()).toEqual({message: "Thanks, it is marked as paid.", replyTo: "board@esa-blueshell.nl"})
    await expect(page.getByTestId("inbox-message-item-2")).toContainText("Mock User, from the site")
    await expect(page.getByTestId("inbox-message-state")).toHaveText("Inbox · Replied")
  })
})
