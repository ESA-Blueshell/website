import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("esports mobile layout", () => {
  test("opens the first team on arrival, and another on a tap", async ({page}) => {
    await installApiMocks(page)
    await page.setViewportSize({width: 390, height: 844})

    await page.goto("/esports/valorant")

    await expect(page).toHaveURL(/\/esports\/valorant$/)
    const first = page.getByTestId("team-roster-1")
    const second = page.getByTestId("team-roster-2")
    await expect(first).toContainText("BS Waterboarders")

    // The first slice opens itself, so the page never lands with everything shut.
    await expect(first.getByRole("button")).toHaveAttribute("aria-expanded", "true")
    await expect(first).toContainText("AriosFury")
    await expect(second.getByRole("button")).toHaveAttribute("aria-expanded", "false")

    // A touch screen has no hover to give, so a tap opens a slice instead.
    await second.getByRole("button").click()

    await expect(second.getByRole("button")).toHaveAttribute("aria-expanded", "true")
    await expect(first.getByRole("button")).toHaveAttribute("aria-expanded", "false")
  })

  test("keeps the seasons on the line, and scrolls to the shown one", async ({page}) => {
    await installApiMocks(page)
    await page.setViewportSize({width: 390, height: 844})

    await page.goto("/esports/valorant")
    const strip = page.getByTestId("esports-season-timeline")
    await strip.waitFor()

    // The strip scrolls rather than shrinking, so a band keeps the width its labels need and
    // the highlighted one says which season it is without a caption underneath.
    await expect(strip.locator(".stop__label--lead").first()).toBeVisible()
    await expect(page.getByTestId("esports-season-caption")).toHaveCount(0)

    const selected = page.locator(".stop--on")
    await expect(selected).toHaveCount(1)
    await expect(selected).toBeInViewport()
  })
})
