import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, loginAsMember} from "./mocks"

const PHONE = {width: 390, height: 844}

test.describe("the Help page", () => {
  test("is the last account tab, and shows a member what a member can do", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())

    await page.goto("/account")
    await page.getByTestId("account-tab-help").click()

    await expect(page).toHaveURL(/\/account\/help$/)
    await expect(page.locator("h1")).toHaveText("Help")
    await expect(page.getByTestId("account-tab-help")).toHaveAttribute("aria-current", "page")
    await expect(page.getByTestId("help-topic-events-sign-up")).toBeVisible()
    await expect(page.getByTestId("help-topic-contributions-pay")).toBeVisible()
    await expect(page.getByTestId("help-topic-events-approve")).toHaveCount(0)
    await expect(page.getByTestId("help-section-people")).toHaveCount(0)
  })

  test("shows the board its own topics, marked as the board's", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/account/help")

    await expect(page.getByTestId("help-topic-events-approve")).toContainText("Board")
    await expect(page.getByTestId("help-topic-recovery-board-manage")).toBeVisible()
    await expect(page.getByTestId("help-section-people")).toBeVisible()
  })

  test("opens a topic from its row, finds one by what is typed and jumps to a section from the list", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())
    await page.goto("/account/help")

    await expect(page.getByText("Type your password again.")).toBeHidden()
    await page.getByTestId("help-open-security-two-factor").click()
    await expect(page.getByText("Type your password again.")).toBeVisible()

    await page.getByTestId("help-jump-esports").click()
    await expect(page.getByTestId("help-section-esports")).toBeInViewport()

    await page.getByTestId("help-search").locator("input").fill("lost phone")
    await expect(page.getByTestId("help-count")).toHaveText("1 result")
    await expect(page.getByTestId("help-jump-recovery-lost-phone")).toBeVisible()
    await expect(page.getByText("Sign in with one of your backup codes.")).toBeVisible()
    await expect(page.getByTestId("help-topic-events-sign-up")).toHaveCount(0)
    // The search was typed far down the page; the answers are read from their start, with nothing below the footer.
    await expect(page.getByTestId("help-topic-recovery-lost-phone")).toBeInViewport()
    const gap = await page.evaluate(() =>
      document.documentElement.scrollHeight - Math.round(document.querySelector("footer")!.getBoundingClientRect().bottom + window.scrollY))
    expect(gap).toBeLessThanOrEqual(1)

    await page.getByTestId("help-search").locator("input").fill("zzz")
    await expect(page.getByTestId("help-nothing")).toBeVisible()
  })

  test("narrows the board's help to one role", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/account/help")

    await page.getByTestId("help-role-picker-search").click()
    await page.getByTestId("help-role-picker-list").getByText("Treasurer").click()

    await expect(page.getByTestId("help-count")).toHaveText("3 results")
    await expect(page.getByTestId("help-topic-contributions-paid")).toBeVisible()
    await expect(page.getByTestId("help-topic-events-sign-up")).toHaveCount(0)
  })

  test("fits a phone without scrolling sideways", {tag: "@phone"}, async ({page}) => {
    await page.setViewportSize(PHONE)
    await installApiMocks(page)
    await loginAsMember(page.context())

    await page.goto("/account/help")

    await expect(page.getByTestId("help-topic-account-details")).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(PHONE.width)
  })
})
