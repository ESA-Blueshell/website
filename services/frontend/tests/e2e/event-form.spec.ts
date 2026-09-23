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

    await page.getByTestId("event-form-approved-field").locator("input[type='checkbox']").check()
    await page.getByTestId("event-form-signup-field").locator("input[type='checkbox']").check()
    const limit = page.getByTestId("event-form-signup-limit-field").locator("input").first()
    await limit.fill("24")
    await limit.press("Tab")
    await expect(limit).toHaveValue("24")

    const created = page.waitForRequest(request => request.method() === "POST" && /\/events$/u.test(new URL(request.url()).pathname))
    await page.getByTestId("event-form-submit-btn").click()
    const body = (await created).postDataJSON() as Record<string, unknown>
    expect(body).toMatchObject({title: "Pub quiz", location: "Café De Beiaard", committeeId: 900, approved: true, signUp: true, signUpLimit: 24})
  })

  test("says what is missing on the field it is missing from", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/events/create")

    await page.getByTestId("event-form-submit-btn").click()

    await expect(page.getByTestId("event-form-title-field").locator(".island-field__said--wrong")).not.toBeEmpty()
  })
})
