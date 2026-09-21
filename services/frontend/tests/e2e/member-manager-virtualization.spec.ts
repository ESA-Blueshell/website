import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

// Comfortably more members than any window can hold, so a table that mounts them all is
// unmistakable from one that mounts a screenful.
const COUNT = 300

// What the table pins every row to, so the spec can name a row by its distance down the list.
const ROW_HEIGHT = 44

const users = Array.from({length: COUNT}, (_, i) => ({
  id: i + 1,
  // Padded so the default name order matches the numbering, which is what lets the scroll
  // assertion below name a member it expects to find far down the list.
  fullName: `Member ${String(i + 1).padStart(3, "0")}`,
  username: `member${String(i + 1).padStart(3, "0")}`,
  firstName: "Member",
  lastName: String(i + 1).padStart(3, "0"),
  email: `member${i + 1}@example.com`,
  enabled: true,
  roles: ["MEMBER"],
}))

const memberships = users.map((user, i) => ({
  id: 1000 + i,
  userId: user.id,
  memberType: "REGULAR",
  startDate: "2024-01-01",
  incasso: false,
}))

const contributionPeriods = [
  {id: 201, startDate: "2025-07-01", endDate: "2025-12-31", halfYearCutoffDate: "2025-10-01", halfYearFee: 15, fullYearFee: 30, alumniFee: 10},
]

const renderedRows = (page: import("./test").Page) =>
  page.locator('[data-testid^="member-manager-row-"]')

// The names the scroller currently has mounted, in order. Read from the name cell rather than
// through a locator so a failed wait reports the window it ended on instead of "not found".
const windowedNames = (page: import("./test").Page) =>
  renderedRows(page).evaluateAll((rows) => rows.map((row) => row.children[1]?.textContent?.trim() ?? ""))

test.describe("member manager virtualization", () => {
  test.beforeEach(async ({page}) => {
    await installApiMocks(page, {users, memberships, contributionPeriods, contributions: []})
    await loginAsAdmin(page.context())
    // The table renders at the lg breakpoint and up; below it the page is a list of cards.
    await page.setViewportSize({width: 1440, height: 900})
    await page.goto("/user-manager")
    await page.getByTestId("member-manager-row-1").waitFor()
  })

  test("mounts a screenful of rows rather than every member", async ({page}) => {
    // The count badge proves all 300 members are loaded and filtered in...
    await expect(page.getByTestId("member-manager-table").getByText(String(COUNT))).toBeVisible()

    // ...while the document holds a fraction of them. The bound is generous: it fails on a
    // table that mounts every row, not on a scroller that buffers a few extra.
    await expect
      .poll(async () => renderedRows(page).count(), {message: "rows mounted"})
      .toBeLessThan(80)
  })

  test("scrolling reaches a member far down the list", async ({page}) => {
    const far = "Member 250"
    await expect(page.getByText(far, {exact: true})).toHaveCount(0)

    const scroller = page.locator(".v-table__wrapper")
    const target = 249 * ROW_HEIGHT

    // The scroller renders its window in response to the scroll event, so a scrollTop that has
    // arrived says nothing about whether the row is in the document yet: waiting on the offset
    // and then asserting the row is a race the row loses (#1375). Wait on the window instead.
    //
    // The offset is re-applied rather than nudged by the distance left, because a nudge
    // dispatched while an earlier one is still settling adds to it and carries the window past
    // the row — which is the other way this assertion used to fail.
    await expect
      .poll(async () => {
        await scroller.evaluate((el, top) => {
          el.scrollTop = top
        }, target)
        return windowedNames(page)
      }, {message: "the members the scroller has mounted"})
      .toContain(far)

    await expect(page.getByText(far, {exact: true})).toBeVisible()
    // Still a window, not the whole list, now that it sits in the middle of it.
    await expect
      .poll(async () => renderedRows(page).count(), {message: "rows mounted while scrolled"})
      .toBeLessThan(80)
  })
})
