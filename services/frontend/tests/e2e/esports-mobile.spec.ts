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

  test("names the season on show, where there is no room to label every node", async ({page}) => {
    await installApiMocks(page)
    await page.setViewportSize({width: 390, height: 844})

    await page.goto("/esports/valorant")

    // The half labels and the years both give way to one caption on a narrow screen.
    await expect(page.getByTestId("esports-season-caption")).toContainText("Autumn 2025/26")
  })
})
