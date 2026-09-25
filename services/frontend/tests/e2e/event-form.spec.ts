import {Buffer} from "node:buffer"
import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, writeMarkdown} from "./mocks"

test.describe("the event form", () => {
  test("adds an event with a committee picked by typing, beside its preview", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/events/create")

    await expect(page.getByRole("heading", {name: "Add an event"})).toBeVisible()
    await page.getByTestId("event-form-title-field").locator("input").first().fill("Pub quiz")
    await page.getByTestId("event-form-location-field").locator("input").first().fill("Café De Beiaard")
    await writeMarkdown(page, "Description*", "Questions, and a round of drinks.")
    await expect(page.getByTestId("event-form-preview")).toContainText("Pub quiz")

    const committee = page.getByTestId("event-form-committee-field")
    await committee.locator("input").first().fill("Events")
    await page.getByRole("listbox").getByText("Events Committee", {exact: true}).click()
    await expect(committee.getByRole("combobox")).toHaveValue("Events Committee")

    // Archived games are not offered for a new pick.
    await page.getByTestId("event-form-games-picker-search").click()
    await expect(page.getByTestId("event-form-games-picker-DOTA_2")).toHaveCount(0)
    await page.getByTestId("event-form-games-picker-CHESS").click()
    await expect(page.getByTestId("event-form-games-CHESS")).toContainText("Chess")

    await page.getByTestId("event-form-approved-field").locator("input[type='checkbox']").check()
    await page.getByTestId("event-form-signup-field").locator("input[type='checkbox']").check()
    const limit = page.getByTestId("event-form-signup-limit-field").locator("input").first()
    await limit.fill("24")
    await limit.press("Tab")
    await expect(limit).toHaveValue("24")

    const created = page.waitForRequest(request => request.method() === "POST" && /\/events$/u.test(new URL(request.url()).pathname))
    await page.getByTestId("event-form-submit-btn").click()
    const body = (await created).postDataJSON() as Record<string, unknown>
    expect(body).toMatchObject({title: "Pub quiz", location: "Café De Beiaard", committeeId: 900, approved: true, signUp: true, signUpLimit: 24, gameCodes: ["CHESS"]})
  })

  test("holds a poster in the picture input and stores it with the event", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/events/create")

    const poster = page.getByTestId("event-form-banner-field")
    await expect(poster.getByTestId("event-form-banner-field-empty")).toHaveText("Choose a poster")
    await poster.getByTestId("event-form-banner-field-file").setInputFiles({
      name: "poster.png",
      mimeType: "image/png",
      buffer: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=", "base64"),
    })
    await expect(poster.getByTestId("event-form-banner-field-preview")).toBeVisible()

    await page.getByTestId("event-form-title-field").locator("input").first().fill("Pub quiz")
    await page.getByTestId("event-form-location-field").locator("input").first().fill("Café De Beiaard")
    await writeMarkdown(page, "Description*", "Questions, and a round of drinks.")
    const committee = page.getByTestId("event-form-committee-field")
    await committee.locator("input").first().fill("Events")
    await page.getByRole("listbox").getByText("Events Committee", {exact: true}).click()

    const stored = page.waitForRequest(request => request.method() === "POST" && new URL(request.url()).pathname.endsWith("/events/banners"))
    const created = page.waitForRequest(request => request.method() === "POST" && /\/events$/u.test(new URL(request.url()).pathname))
    await page.getByTestId("event-form-submit-btn").click()
    await stored
    expect((await created).postDataJSON()).toMatchObject({banner: {fileId: 77}})
  })

  test("says what is missing on the field it is missing from", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/events/create")

    await page.getByTestId("event-form-submit-btn").click()

    await expect(page.getByTestId("event-form-title-field").locator(".island-field__said--wrong")).not.toBeEmpty()
  })
})
