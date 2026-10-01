import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("the Inbox", () => {
  test("lists replies to the site's emails and mail to other addresses, from the sidebar", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/management/mail/sent")

    await page.getByRole("link", {name: "Inbox"}).click()
    await expect(page).toHaveURL(/\/management\/mail\/inbox$/)
    await expect(page.getByTestId("inbox-new")).toHaveText("2")
    await expect(page.getByTestId("inbox-follows-1")).toHaveText("Answers contribution reminder")
    await expect(page.getByTestId("inbox-follows-2")).toHaveText("To partners@")
  })
})
