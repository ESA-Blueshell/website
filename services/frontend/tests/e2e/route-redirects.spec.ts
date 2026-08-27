import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("route redirects", () => {
  test("redirects esports root and events calendar routes to canonical destinations", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports")
    await expect(page).toHaveURL(/\/esports\/competitive-scene$/)
    // The destination is the esports index. Asserting its root rather than a
    // heading: the wording of the page is design, the island around it is not.
    await expect(page.getByTestId("esports-island")).toBeVisible()

    await page.goto("/events/calendar")
    await expect(page).toHaveURL(/\/events(\?.*)?$/)
    expect(new URL(page.url()).pathname).toBe("/events")
    await expect(page.getByText("Upcoming Events", {exact: true}).first()).toBeVisible()
  })

  test("rewrites legacy auth routes and strips recovery tokens from URL", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/account/reset-password/legacy-user/legacy-reset-token")
    await expect(page).toHaveURL(/\/account\/reset-password$/)
    const resetPasswordUrl = new URL(page.url())
    expect(resetPasswordUrl.pathname).toBe("/account/reset-password")
    expect(resetPasswordUrl.searchParams.get("token")).toBeNull()
    expect(resetPasswordUrl.hash).not.toContain("token=")

    await page.goto("/account/activate/member/legacy-member-token")
    await expect(page).toHaveURL(/\/account\/activate\/member$/)
    const memberActivationUrl = new URL(page.url())
    expect(memberActivationUrl.searchParams.get("token")).toBeNull()
    expect(memberActivationUrl.hash).not.toContain("token=")

    await page.goto("/account/activate/user/legacy-user/legacy-user-token")
    await expect(page).toHaveURL(/\/account\/activate\/user$/)
    const userActivationUrl = new URL(page.url())
    expect(userActivationUrl.searchParams.get("token")).toBeNull()
    expect(userActivationUrl.hash).not.toContain("token=")
  })

  test("renders not-found page for unknown routes", async ({page}) => {
    await installApiMocks(page)
    const path = `/missing-route-${Date.now()}`

    await page.goto(path)

    await expect(page).toHaveURL(new RegExp(`${path}$`))
    await expect(page.getByText(/Uh oh, we made a fucky wucky!/i)).toBeVisible()
    await expect(page.getByRole("link", {name: "here"})).toHaveAttribute("href", "/")
  })
})
