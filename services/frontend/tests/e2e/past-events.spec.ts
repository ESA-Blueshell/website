import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/* Thirty events a week apart, the newest yesterday: more than one page of the archive. */
const past = Array.from({length: 30}, (_, i) => {
  const at = new Date(Date.now() - (1 + i * 7) * 86_400_000)
  return {
    id: 700 + i,
    title: i === 0 ? "4Funcie Pooling" : `Game night ${i}`,
    startTime: at.toISOString(),
    endTime: at.toISOString(),
    location: "Esports Lounge Twente",
    approved: true,
    signUp: false,
    signUpCount: 0,
    membersOnly: false,
    committeeId: i % 2 === 0 ? 900 : null,
  }
})

test.describe("the past events archive", () => {
  test("is a press away from the events page", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/events")
    await page.getByTestId("events-past-link").click()

    await expect(page).toHaveURL(/\/events\/past$/)
    await expect(page.getByRole("heading", {level: 1})).toHaveText("Past events")
  })

  test("shows the newest events first, a page at a time, each leading to its own page", async ({page}) => {
    await installApiMocks(page, {events: past})

    await page.goto("/events/past")

    await expect(page.getByTestId("event-archive-shown")).toHaveText("Showing 24 of 30")
    await expect(page.getByTestId("event-archive-tile-700")).toContainText("Events Committee")
    await expect(page.getByTestId("event-archive-tile-701")).toContainText("Member's initiative")
    await expect(page.getByTestId("event-archive-year-all")).toHaveAttribute("aria-checked", "true")

    await page.getByTestId("event-archive-older").click()
    await expect(page.getByTestId("event-archive-shown")).toHaveText("Showing 30 of 30")
    await expect(page.getByTestId("event-archive-older")).toHaveCount(0)

    await page.getByTestId("event-archive-tile-700").click()
    await expect(page).toHaveURL(/\/events\/700$/)
  })

  test("searches by title, and says so when nothing is called that", async ({page}) => {
    await installApiMocks(page, {events: past})

    await page.goto("/events/past")
    await expect(page.getByTestId("event-archive-shown")).toHaveText("Showing 24 of 30")

    await page.getByTestId("event-archive-search").fill("pool")
    await expect(page.getByTestId("event-archive-shown")).toHaveText("Showing 1 of 1")
    await expect(page.getByTestId("event-archive-tile-700")).toBeVisible()

    await page.getByTestId("event-archive-search").fill("karaoke")
    await expect(page.getByTestId("event-archive-none")).toHaveText("No past event is called anything like “karaoke”.")
  })
})
