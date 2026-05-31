import {readFileSync} from "node:fs"
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
    const eventCard = page.getByTestId("event-card-500").first()
    await expect(eventCard).toBeVisible()

    await eventCard.getByTestId("event-signups-btn-500").click()
    await expect(page).toHaveURL(/\/events\/signups\/500/)

    await page.goto("/events")
    const eventCardAfterReturn = page.getByTestId("event-card-500").first()
    await expect(eventCardAfterReturn).toBeVisible()
    await eventCardAfterReturn.getByTestId("event-edit-btn-500").click()
    await expect(page).toHaveURL(/\/events\/edit\/500/)
  })

  // Pin the zone so the formatted "Submitted at" column is deterministic across machines.
  test.use({timezoneId: "UTC"})

  test("exports the event sign-ups as a CSV download", async ({page}) => {
    await installApiMocks(page, {
      eventDetailsById: {
        "500": {
          id: 500,
          title: "Game Night",
          approved: true,
          signUp: true,
          membersOnly: false,
          committeeId: 900,
          signUpForm: {
            id: 1,
            responseCount: 1,
            questions: [
              {id: 10, idx: 0, type: "OPEN", label: "Why join", surveyId: 1},
              {id: 11, idx: 1, type: "CHECKBOX", label: "Snacks", choiceLabels: ["Pizza", "Chips"], surveyId: 1},
            ],
          },
        },
      },
      eventSignUpsByEventId: {
        "500": [
          {
            id: 600,
            eventId: 500,
            createdAt: "2026-02-20T12:34:00.000Z",
            user: {id: 1, fullName: "Ada Lovelace", discord: "ada#0001", email: "ada@example.com", phoneNumber: "0612345678"},
            answers: [
              {id: 1, questionId: 10, textResponse: "Love games"},
              {id: 2, questionId: 11, optionSelections: [true, false]},
            ],
          },
        ],
      },
    })
    await loginAsBoard(page.context())

    await page.goto("/events/signups/500")
    const exportButton = page.getByTestId("export-csv-btn")
    await expect(exportButton).toBeEnabled({timeout: 30_000})

    const downloadPromise = page.waitForEvent("download")
    await exportButton.click()
    const download = await downloadPromise

    expect(download.suggestedFilename()).toBe("Game-Night-signups.csv")

    const contents = readFileSync(await download.path(), "utf-8")
    const lines = contents.split("\r\n")
    expect(lines[0]).toBe("Submitted at,Name,Discord,Email,Phone,Why join,Snacks")
    expect(lines[1]).toBe("2026-02-20 12:34,Ada Lovelace,ada#0001,ada@example.com,0612345678,Love games,Pizza")
  })
})
