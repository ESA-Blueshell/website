import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * Saving a committee whose member list has not arrived yet.
 *
 * The manager fetches its users after the form is already open. The picker is given a member's
 * id and cannot name them until that list lands, and a save in the meantime has to reach the
 * api all the same — the id is on the model, and the reader changed a name, not a member.
 *
 * A system test found this as `updates committee name and description` failing with a submit
 * that produced no request at all: the form judged itself invalid because the picker had
 * reported the member it could not yet name as no member.
 */
const COMMITTEE = {
  id: 900,
  name: "Events Committee",
  description: "Runs the events, and has done for years.",
  version: 3,
  members: [{userId: 1, role: "Chair"}],
}

test.describe("the committee manager", () => {
  test("saves a name change while the member list is still on its way", async ({page}) => {
    await installApiMocks(page, {committees: [COMMITTEE]})
    await loginAsBoard(page.context())

    // Later routes win, so this one holds the user list back without touching the rest. Matched
    // on the path rather than by glob: the manager asks for `/users` with no query at all, and
    // a pattern expecting one silently matches nothing and holds nothing back.
    await page.route(url => url.pathname.endsWith("/users"), async route => {
      if (route.request().method() !== "GET") return route.fallback()
      await new Promise(resolve => setTimeout(resolve, 3000))
      return route.fallback()
    })

    // Waited for before the page is opened, so a save that lands early is not missed — which
    // means its budget also covers opening the page. A cold dev server compiles this route on
    // the first visit, and on a loaded runner that alone outran the default five seconds.
    const saved = page.waitForRequest(
      request => request.method() === "PUT" && /\/committees\/900$/.test(new URL(request.url()).pathname),
      {timeout: 60_000},
    )

    await page.goto("/committees/manage")
    await page.getByTestId("committee-edit-btn-900").click()
    await page.getByLabel("Committee name").fill("Events Committee Renamed")
    await page.getByLabel("Description").fill("A description long enough to satisfy the rule.")

    await page.getByTestId("committee-form-submit-btn").click()

    const request = await saved
    expect(JSON.parse(request.postData() ?? "{}")).toMatchObject({
      name: "Events Committee Renamed",
      description: "A description long enough to satisfy the rule.",
      members: [{userId: 1, role: "Chair"}],
    })
  })
})
