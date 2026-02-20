import {expect, test} from "./test"
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

  test("event card action buttons navigate to signups and edit routes", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/events")
    const eventCard = page.locator(".v-card").filter({hasText: "Mock Event"}).first()
    await expect(eventCard).toBeVisible()

    await eventCard.locator("button:has(i.mdi-list-status)").first().click()
    await expect(page).toHaveURL(/\/events\/signups\/500/)

    await page.goto("/events")
    const eventCardAfterReturn = page.locator(".v-card").filter({hasText: "Mock Event"}).first()
    await expect(eventCardAfterReturn).toBeVisible()
    await eventCardAfterReturn.locator("button:has(i.mdi-pencil)").first().click()
    await expect(page).toHaveURL(/\/events\/edit\/500/)
  })
})
