import {Buffer} from "node:buffer"
import {expect, test} from "./test"
import {installApiMocks, loginAsMember} from "./mocks"

const PHONE = {width: 390, height: 844}

/* A receipt the size of a thumbnail: four beige pixels are enough for the picker to draw. */
const RECEIPT = {
  name: "receipt.png",
  mimeType: "image/png",
  buffer: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAIAAAAmkwkpAAAAEUlEQVR4nGP48PoOHDEQxwEAaxArcRpK37oAAAAASUVORK5CYII=", "base64"),
}

test.describe("declarations", () => {
  test("a member declares an expense with a cropped receipt, paid to the bank account they add, and withdraws it", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())

    await page.goto("/account/declarations/new")
    await expect(page.getByTestId("declaration-no-bank")).toBeVisible()
    await page.getByTestId("declaration-submit").click()
    await expect(page.getByTestId("declaration-expense-0-receipts-wrong")).toBeVisible()

    await page.getByTestId("declaration-add-bank").click()
    await page.getByTestId("bank-account-holder").locator("input").fill("M. Member")
    await page.getByTestId("bank-account-iban-input").pressSequentially("NL91ABNA0417164300")
    await page.getByTestId("bank-account-save").click()
    await expect(page.getByTestId("bank-account-preview")).toContainText("NL •• … ••00")

    await page.getByTestId("account-tab-declarations").click()
    await page.getByTestId("declarations-new").click()
    await expect(page.getByTestId("declaration-no-bank")).toHaveCount(0)
    await page.getByTestId("declaration-expense-0-day").locator("input").first().fill("10/10/2026")
    await page.getByTestId("declaration-expense-0-amount").locator("input").fill("12,50")
    await page.getByTestId("declaration-expense-0-description").locator("textarea").fill("Pizza for the committee meeting")
    await page.getByTestId("declaration-expense-0-receipts-file").setInputFiles(RECEIPT)
    await page.getByTestId("receipt-crop-done").click()
    await expect(page.getByTestId("declaration-expense-0-receipt-0-preview")).toBeVisible()
    await page.getByTestId("declaration-confirm").click()
    await expect(page.getByTestId("declaration-edit-total")).toContainText("€ 12.50")

    await page.getByTestId("declaration-submit").click()
    await expect(page.getByTestId("declaration-state")).toHaveText("Submitted")

    await page.getByTestId("declaration-withdraw").click()
    await page.getByTestId("declaration-withdraw-dialog").getByRole("button", {name: "Withdraw"}).click()
    await expect(page.getByTestId("declaration-edit")).toBeVisible()

    await page.getByTestId("account-crumb").click()
    await expect(page.getByTestId("declarations-drafts")).toContainText("1")
  })

  test("fits a phone without scrolling sideways @phone", async ({page}) => {
    await page.setViewportSize(PHONE)
    await installApiMocks(page)
    await loginAsMember(page.context())

    await page.goto("/account/declarations/new")

    await expect(page.getByTestId("declaration-expense-0")).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })
})
