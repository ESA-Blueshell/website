import {expect, test} from "./test"
import {installApiMocks, loginAsMember} from "./mocks"

const PHONE = {width: 390, height: 844}

test.describe("the payment method page", () => {
  test("a member adds a bank account, sets up direct debit and stops it again", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())

    await page.goto("/account/payment-details")
    await page.getByTestId("payment-details-method-row").click()
    await expect(page).toHaveURL(/\/account\/payment-details\/payment-method$/)
    await expect(page.getByTestId("payment-method")).toContainText("How do you want to pay?")

    await page.getByTestId("payment-method-choice-debit").check()
    await expect(page.getByTestId("payment-method-no-bank")).toContainText("Add a bank account first")
    await page.getByTestId("payment-method-add-bank").click()
    await page.getByTestId("bank-account-holder").locator("input").fill("Ann Vos")
    await page.getByTestId("bank-account-iban-input").pressSequentially("NL91ABNA0417164300")
    await page.getByTestId("bank-account-save").click()
    await expect(page.getByTestId("bank-account-preview")).toContainText("NL •• … ••00")

    await page.goto("/account/payment-details/payment-method")
    await page.getByTestId("payment-method-choice-debit").check()
    await expect(page.getByTestId("payment-method-account")).toHaveText("NL •• … ••00")
    await page.getByTestId("payment-method-agree").check()
    await page.getByTestId("payment-method-set-up").click()
    await expect(page.getByTestId("payment-method-on")).toContainText("Since 19 Oct 2026")
    await expect(page.getByTestId("payment-method")).toContainText("Direct debit reference BLUESHELL-1-20261019")

    await page.getByTestId("payment-method-choice-manual").check()
    await page.getByTestId("payment-method-stop").click()
    await page.getByTestId("confirm-go").click()
    await expect(page.getByTestId("payment-method-how-to-pay")).toBeVisible()
    await page.getByTestId("payment-method-how-to-pay").click()
    await expect(page).toHaveURL(/\/account\/payment-details#how-to-pay$/)
  })

  test("fits a phone without scrolling sideways", async ({page}) => {
    await page.setViewportSize(PHONE)
    await installApiMocks(page)
    await loginAsMember(page.context())
    const fits = async () => expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(PHONE.width)

    await page.goto("/account/payment-details/payment-method")
    await page.getByTestId("payment-method-choice-debit").check()
    await expect(page.getByTestId("payment-method-no-bank")).toBeVisible()
    await fits()

    await page.getByTestId("payment-method-add-bank").click()
    await page.getByTestId("bank-account-holder").locator("input").fill("Ann Vos")
    await page.getByTestId("bank-account-iban-input").pressSequentially("NL91ABNA0417164300")
    await page.getByTestId("bank-account-save").click()
    await expect(page.getByTestId("bank-account-preview")).toBeVisible()
    await page.goto("/account/payment-details/payment-method")
    await page.getByTestId("payment-method-choice-debit").check()
    await expect(page.getByTestId("payment-method-form")).toBeVisible()
    await fits()

    await page.getByTestId("payment-method-agree").check()
    await page.getByTestId("payment-method-set-up").click()
    await expect(page.getByTestId("payment-method-on")).toBeVisible()
    await fits()
  })
})
