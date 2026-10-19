import {expect, test} from "./test"
import {installApiMocks, loginAsMember} from "./mocks"

test.describe("the bank account page", () => {
  test("a member adds their bank account from Payment details and sees only its preview", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())

    await page.goto("/account/payment-details")
    await expect(page.getByTestId("payment-details-bank-account-row")).toContainText("Not added")
    await page.getByTestId("payment-details-bank-account-row").click()
    await expect(page).toHaveURL(/\/account\/payment-details\/bank-account$/)

    await page.getByTestId("bank-account-holder").locator("input").fill("Ann Vos")
    await page.getByTestId("bank-account-iban-input").pressSequentially("NL91ABNA0417164300")
    await page.getByTestId("bank-account-save").click()

    await expect(page.getByTestId("bank-account-preview")).toContainText("NL •• … ••00")
    await page.goto("/account/payment-details")
    await expect(page.getByTestId("payment-details-bank-account-row")).toContainText("NL •• … ••00")
  })
})
