import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("writing an email", () => {
  test("addresses a group, previews the email as the api renders it, and sends one per person", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/management/mail/sent")

    await page.getByTestId("sent-emails-write").click()
    await expect(page).toHaveURL(/\/management\/mail\/write$/)

    await page.getByTestId("write-to-picker-search").fill("Active")
    await page.getByRole("option", {name: /Active members 2026-2027/}).click()
    await expect(page.getByTestId("write-reach")).toHaveText("Reaches 217 people; 3 without an email address are left out.")

    await page.getByTestId("write-subject").locator("input").fill("LAN night is back")
    await page.getByTestId("write-message").locator(".cm-content").click()
    await page.keyboard.type("Hi everyone")
    await expect(page.getByTestId("write-preview")).toHaveAttribute("srcdoc", /LAN night is back/)

    const sent = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith("/mail/send"))
    await page.getByTestId("write-send").click()
    expect((await sent).postDataJSON()).toMatchObject({to: [{kind: "COHORT", id: "ACTIVE_MEMBERS:4"}], subject: "LAN night is back"})
    await expect(page).toHaveURL(/\/management\/mail\/sent$/)
  })
})
