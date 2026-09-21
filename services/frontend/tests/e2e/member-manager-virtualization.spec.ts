import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

// Comfortably more members than any window can hold, so a table that mounts them all is
// unmistakable from one that mounts a screenful.
const COUNT = 300

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

// Puts the row this many indexes down the list at the middle of the window rather than at its
// top edge. The scroller re-estimates its item height from the rows it has mounted, so an
// offset it agreed with a moment ago can move by a row: a row at the edge is evicted by that,
// a row in the middle is not.
async function centreOn(page: import("./test").Page, index: number) {
  await page.locator(".v-table__wrapper").evaluate((el, i) => {
    const row = el.querySelector('[data-testid^="member-manager-row-"]')
    // Measured rather than assumed: the row height is the table's to choose, and a spec that
    // hardcodes it scrolls somewhere else entirely the day it changes.
    const rowHeight = row ? row.getBoundingClientRect().height : 0
    if (!rowHeight) return
    el.scrollTop = Math.max(0, i * rowHeight - el.clientHeight / 2 + rowHeight / 2)
  }, index)
}

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
    const farIndex = 249
    const far = "Member 250"
    await expect(page.getByText(far, {exact: true})).toHaveCount(0)

    // The scroller renders its window in response to the scroll event, so a scrollTop that has
    // arrived says nothing about whether the row is in the document yet (#1375). The scroll,
    // the wait and the assertion are one retried step: anything asserted after the wait rather
    // than inside it races the scroller's next re-render, and reports "element(s) not found"
    // instead of the window it ended on.
    //
    // The offset is re-applied rather than nudged by the distance left, because a nudge
    // dispatched while an earlier one is still settling adds to it and carries the window past
    // the row.
    await expect
      .poll(
        async () => {
          await centreOn(page, farIndex)
          const names = await windowedNames(page)
          if (!names.includes(far)) return names
          return (await page.getByText(far, {exact: true}).isVisible()) ? far : names
        },
        {message: `the members the scroller has mounted, waiting for ${far}`},
      )
      .toBe(far)

    // Still a window, not the whole list, now that it sits in the middle of it.
    await expect
      .poll(async () => renderedRows(page).count(), {message: "rows mounted while scrolled"})
      .toBeLessThan(80)
  })
})
