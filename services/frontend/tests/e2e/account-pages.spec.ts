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

  test("count sign-ins on their heading, the badge on its last word on a phone", async ({page}) => {
    await page.setViewportSize(PHONE)
    await installApiMocks(page)
    await loginAsMember(page.context())

    await page.goto("/account/security/sign-ins")

    const heading = await page.getByRole("heading", {name: /Signed in/u}).boundingBox()
    const badge = await page.getByTestId("security-sign-ins-count").boundingBox()
    expect(badge!.y).toBeLessThan(heading!.y + heading!.height)
    expect(badge!.x + badge!.width).toBeLessThanOrEqual(PHONE.width)
    expect(badge!.x).toBeGreaterThan(heading!.x)
  })

  test("move the address from a page of its own", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())

    await page.goto("/account/security/email")

    await expect(page.getByTestId("security-email-now")).toContainText("mock-user@example.com")
    await expect(page.getByTestId("security-change-email-btn")).toBeDisabled()
    await page.getByTestId("security-new-email-field").locator("input").fill("moved@example.com")
    await expect(page.getByTestId("security-change-email-btn")).toBeEnabled()
  })

  test("change the password from a page of its own", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())
    let changed: unknown = null
    await page.route("**/api/users/me/password", async (route) => {
      changed = route.request().postDataJSON()
      await route.fulfill({status: 204})
    })

    await page.goto("/account/security")
    await page.getByTestId("security-password").click()
    await page.getByTestId("security-current-password-field").locator("input").fill("Secret123!")
    await page.getByTestId("security-new-password-field").locator("input").fill("Another123!")
    await page.getByTestId("security-change-password-btn").click()

    await expect(page.getByText("Your password is changed. Every other sign-in has ended.")).toBeVisible()
    expect(changed).toEqual({currentPassword: "Secret123!", newPassword: "Another123!"})
  })

  test("end one other sign-in and forget a trusted browser", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())
    const now = new Date().toISOString()
    const ended: string[] = []
    await page.route("**/api/users/me/sign-ins", route => route.fulfill({json: [
      {id: "here", browser: "Chrome", platform: "Linux", signedInAt: now, lastSeenAt: now, current: true},
      {id: "phone", browser: "Safari", platform: "iOS", signedInAt: now, lastSeenAt: now, current: false},
    ]}))
    await page.route("**/api/users/me/trusted-browsers", route => route.fulfill({json: [
      {id: 4, browser: "Chrome", platform: "Linux", trustedAt: now, expiresAt: now, lastUsedAt: null},
    ]}))
    await page.route(url => /\/users\/me\/(sign-ins\/phone|trusted-browsers\/4)$/u.test(url.pathname), async (route) => {
      ended.push(new URL(route.request().url()).pathname)
      await route.fulfill({status: 204})
    })

    await page.goto("/account/security/sign-ins")
    await expect(page.getByTestId("security-sign-ins-count")).toContainText("2")
    await page.getByTestId("security-sign-in").filter({hasText: "Safari on iOS"}).getByRole("button", {name: "Sign out"}).click()
    await page.getByTestId("security-trusted-browser").getByRole("button", {name: "Forget"}).click()

    await expect.poll(() => ended).toEqual(["/api/users/me/sign-ins/phone", "/api/users/me/trusted-browsers/4"])
  })

  test("read the security log by day, a page at a time", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())
    const day = (daysAgo: number) => new Date(Date.now() - daysAgo * 86_400_000).toISOString()
    await page.route("**/api/users/me/security-events**", async (route) => {
      const older = new URL(route.request().url()).searchParams.get("page") === "1"
      await route.fulfill({json: older
        ? {events: [{id: 1, kind: "PASSWORD_CHANGED", actorKind: "PERSON", occurredAt: day(20)}], page: 1, totalPages: 2, totalElements: 3}
        : {events: [
          {id: 3, kind: "SIGNED_IN", actorKind: "PERSON", occurredAt: day(0), browser: "Chrome", platform: "Linux"},
          {id: 2, kind: "ROLES_CHANGED", actorKind: "PERSON", actorName: "Ro Admin", occurredAt: day(1)},
        ], page: 0, totalPages: 2, totalElements: 3}})
    })

    await page.goto("/account/security/log")
    await expect(page.getByTestId("security-log-day")).toHaveText(["Today", "Yesterday"])
    await expect(page.getByTestId("security-log-entry").nth(1)).toContainText("by Ro Admin")

    await page.getByTestId("security-log-older-btn").click()
    await expect(page.getByTestId("security-log-entry")).toHaveCount(3)
    await expect(page.getByTestId("security-log-older-btn")).toHaveCount(0)
  })
})
