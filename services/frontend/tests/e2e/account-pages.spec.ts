import {expect, test} from "./test"
import {installApiMocks, loginAsMember, preferLightTheme} from "./mocks"

const PHONE = {width: 390, height: 844}

test.describe("the account pages", () => {
  test("share one header whose tabs move between them", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())

    await page.goto("/account")
    await expect(page.getByTestId("account-tab-account")).toHaveAttribute("aria-current", "page")

    await page.getByTestId("account-tab-games").click()
    await expect(page).toHaveURL(/\/account\/games$/)
    await expect(page.getByTestId("account-tab-games")).toHaveAttribute("aria-current", "page")
    await expect(page.getByTestId("account-tab-account")).not.toHaveAttribute("aria-current", "page")

    await page.getByTestId("account-tab-address").click()
    await expect(page).toHaveURL(/\/account\/addresses\/10$/)

    await page.getByTestId("account-tab-security").click()
    await expect(page).toHaveURL(/\/account\/security$/)
    await expect(page.locator("h1")).toHaveText("Security")
  })

  test("keep every tab on one row on a phone, scrolling sideways when they do not fit", async ({page}) => {
    await page.setViewportSize(PHONE)
    await installApiMocks(page)
    await preferLightTheme(page)
    await loginAsMember(page.context())

    await page.goto("/account/games")

    const tops = await page.locator("[data-testid^=account-tab-]").evaluateAll(tabs =>
      tabs.map(tab => Math.round(tab.getBoundingClientRect().top)))
    expect(new Set(tops).size).toBe(1)
    await expect(page.getByTestId("account-tab-games")).toBeInViewport()
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(PHONE.width)
  })
})
