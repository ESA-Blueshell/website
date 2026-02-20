import {expect, test} from "@playwright/test"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("events page", () => {
  test("renders event cards with mocked API data", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/events")

    await expect(page.getByText("Upcoming Events", {exact: true}).first()).toBeVisible({timeout: 30_000})
    await expect(page.getByText("Mock Event").first()).toBeVisible()
    await expect(page.getByText("Events Committee").first()).toBeVisible()

    await page.getByRole("link", {name: /create new event/i}).click()
    await expect(page).toHaveURL(/\/events\/create/)
  })
})
