import type {Locator} from "@playwright/test"
import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

// Subject 102 in the mocks is the committee cohort: one member, one Brevo mapping, one
// enabled rule — enough for every section to have something to count.
const COMMITTEE_SUBJECT = "/management/cohorts/subjects/102"

/** The number a box wears on its heading. */
const badgeOf = (box: Locator) => box.getByTestId("info-box-count").locator(".v-badge__badge")

test.describe("cohort subject detail", () => {
  test("renders every section as a manager card", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    await expect(page.getByTestId("cohort-subject-identity")).toBeVisible()
    await expect(page.getByTestId("cohort-subject-definition")).toBeVisible()
    await expect(page.getByTestId("cohort-subject-targets")).toBeVisible()
    await expect(page.getByTestId("cohort-subject-members")).toBeVisible()
  })

  test("names the cohort kind the way the category page does", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    await expect(page.getByTestId("cohort-subject-identity")).toContainText("Web Cmte")
  })

  test("each box says how much it holds without being opened", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    // Every count on the page is a badge on the heading it belongs to, and nothing repeats it
    // as a line underneath. The page's own counts members, so the two rows the target holds
    // and we do not are not in it.
    await expect(page.getByTestId("cohort-subject-member-count")).toContainText("2")
    await expect(badgeOf(page.getByTestId("cohort-subject-targets"))).toHaveText("1")
    await expect(page.getByTestId("cohort-subject-targets")).not.toContainText("1 sync target")
  })

  test("names the definition that decides who belongs", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    // A name to read, rather than a fact kind and a key to interpret.
    const definition = page.getByTestId("cohort-subject-definition")
    await expect(definition).toContainText("COMMITTEE_MEMBERS:42")
    await expect(page.getByTestId("cohort-subject-orphaned")).toHaveCount(0)
  })

  test("says so when nothing produces the cohort any more", async ({page}) => {
    await installApiMocks(page, {
      cohortSubjectDetail: {definitionKey: "COMMITTEE_MEMBERS:42", orphaned: true},
    })
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    // Its committee was disbanded: the list is still there, and nothing syncs to it.
    await expect(page.getByTestId("cohort-subject-orphaned")).toBeVisible()
  })

  test("opens a box to show what it holds, and keeps it shut until asked", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    const targets = page.getByTestId("cohort-subject-targets")
    await expect(targets.getByTestId("cohort-subject-target-brevo")).toHaveCount(0)

    await targets.getByTestId("info-box-toggle").first().click()

    await expect(targets.getByTestId("cohort-subject-target-brevo")).toBeVisible()
  })

  test("keeps the add-target action on the header row of the targets table", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    // Adding a target is one of the table's own actions, so it sits in the header row's menu
    // rather than on the box around it.
    const targets = page.getByTestId("cohort-subject-targets")
    await targets.getByTestId("info-box-toggle").first().click()
    await expect(targets.getByTestId("cohort-subject-add-target")).toHaveCount(0)

    await targets.getByTestId("cohort-subject-targets-menu").click()
    await page.getByTestId("cohort-subject-add-target").click()
    await expect(page.getByTestId("target-picker-modal")).toBeVisible()
  })

  test("puts a target's own actions behind one menu", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    const targets = page.getByTestId("cohort-subject-targets")
    await targets.getByTestId("info-box-toggle").first().click()

    // Two outlined buttons per target became one menu holding both.
    await expect(targets.getByTestId("cohort-subject-switch-target-brevo")).toHaveCount(0)
    await targets.getByTestId("cohort-subject-target-menu-brevo").click()
    await expect(page.getByTestId("cohort-subject-switch-target-brevo")).toBeVisible()
    await expect(page.getByTestId("cohort-subject-inbound-reconcile-brevo")).toBeVisible()
  })

  test("the category page names each cohort's kind on its own row", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/cohorts/committees")

    // The kind used to be a heading repeated above each run of rows, which put the same words
    // on screen twice. It is a column now, and the counts are columns beside it.
    const cells = page.getByTestId("cohort-subject-row-102").locator("td")
    await expect(cells.nth(0)).toHaveText("Web Cmte")
    await expect(cells.nth(1)).toHaveText("Committee members")
    await expect(cells.nth(2)).toHaveText("1")
    await expect(cells.nth(3)).toHaveText("1")
    await expect(page.getByTestId("cohort-type-group-COMMITTEE_MEMBERS")).toHaveCount(0)
  })

  test("the category page counts its cohorts beside one heading", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/cohorts/periods")

    // One heading carrying the count, as the member table does — not a heading, an eyebrow
    // repeating it, and a subtitle holding the number.
    await expect(page.getByTestId("cohort-subject-count")).toContainText("1")
    await expect(page.getByRole("heading", {name: "Periods – Cohorts"})).toBeVisible()
  })

  test("the category table sorts on a column when its header is clicked", async ({page}) => {
    await installApiMocks(page, {
      cohortSubjects: [
        {id: 201, type: "PERIOD_MEMBERS", category: "PERIODS", label: "Alpha", memberCount: 9, mappingCount: 1},
        {id: 202, type: "PERIOD_PAYERS", category: "PERIODS", label: "Beta", memberCount: 2, mappingCount: 1},
      ],
    })
    await loginAsAdmin(page.context())

    await page.goto("/management/cohorts/periods")

    const names = page.locator('[data-testid^="cohort-subject-row-"] td:first-child')
    // Unsorted, the rows read in the order the kinds are declared: members before payers.
    await expect(names).toHaveText(["Alpha", "Beta"])

    await page.getByTestId("cohort-header-memberCount").click()
    await expect(names).toHaveText(["Beta", "Alpha"])

    await page.getByTestId("cohort-header-memberCount").click()
    await expect(names).toHaveText(["Alpha", "Beta"])
  })

  test("goes back to the category the cohort belongs to", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)
    await page.getByTestId("cohort-subject-back").click()

    await expect(page).toHaveURL(/\/management\/cohorts\/committees$/)
  })
})

/**
 * Drift is not a panel any more. A cohort's members and the rows only its target knows about
 * are one table, and each row says which of the two it is.
 */
test.describe("cohort subject detail — drift in the members table", () => {
  const SUBJECT = "/management/cohorts/subjects/101"

  const openMembers = async (page: import("./test").Page) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())
    await page.goto(SUBJECT)
    const members = page.getByTestId("cohort-subject-members")
    await members.getByTestId("cohort-subject-member-list").waitFor()
    return members
  }

  test("says what each row is, and chips only the ones that differ", async ({page}) => {
    const members = await openMembers(page)

    // In sync: stated, but quietly — a cohort of healthy rows should not be a wall of colour.
    const inSync = members.getByTestId("cohort-subject-member-sync-301")
    await expect(inSync).toHaveText("In sync")
    await expect(inSync.locator(".v-chip")).toHaveCount(0)

    // The exceptions carry a chip, and each names the system it is out of step with.
    await expect(members.getByTestId("cohort-subject-member-sync-401").locator(".v-chip"))
      .toHaveText("Not in Brevo yet")
    await expect(members.getByTestId("cohort-subject-member-sync-601").locator(".v-chip"))
      .toHaveText("Only in Brevo")
  })

  test("names a row the target knows and we can identify, and shows the rest by its label", async ({page}) => {
    const members = await openMembers(page)

    // A stranger whose external id maps to an account is that person, not an opaque id.
    await expect(members.getByTestId("cohort-subject-member-501")).toContainText("Casper Known")
    // One nothing local claims falls back to whatever the external system calls it.
    await expect(members.getByTestId("cohort-subject-member-601")).toContainText("someone@example.com")
  })

  test("counts members in the badge, not rows", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())
    await page.goto(SUBJECT)

    // Four rows, two of them members: the badge counts people, by the same predicate the
    // category page counts in SQL, so the two pages agree.
    await expect(page.getByTestId("cohort-subject-member-count")).toContainText("2")
    await expect(page.getByTestId("cohort-subject-members").locator("tbody tr")).toHaveCount(4)
  })

  test("filters down to the rows that need attention", async ({page}) => {
    const members = await openMembers(page)
    await expect(members.locator("tbody tr")).toHaveCount(4)

    await members.getByTestId("cohort-member-filter-sync").click()
    await page.getByRole("option", {name: "Needs attention"}).click()

    // The healthy row goes; the three that differ stay.
    await expect(members.locator("tbody tr")).toHaveCount(3)
    await expect(members.getByTestId("cohort-subject-member-sync-301")).toHaveCount(0)
  })

  test("searches across the names, the addresses and the external identity", async ({page}) => {
    const members = await openMembers(page)
    const search = members.getByTestId("cohort-member-search").locator("input")

    await search.fill("emma")
    await expect(members.locator("tbody tr")).toHaveCount(1)
    await expect(members.getByTestId("cohort-subject-member-301")).toBeVisible()

    // A row with no local account is findable by what the external system calls it, which is
    // the only identity it has.
    await search.fill("someone@")
    await expect(members.locator("tbody tr")).toHaveCount(1)
    await expect(members.getByTestId("cohort-subject-member-601")).toBeVisible()

    // Clearing with the field's button writes null rather than "", and must read as no search.
    await members.getByTestId("cohort-member-search").locator(".v-field__clearable").click()
    await expect(members.locator("tbody tr")).toHaveCount(4)
  })

  test("narrows by search and state together", async ({page}) => {
    const members = await openMembers(page)

    await members.getByTestId("cohort-member-search").locator("input").fill("e")
    await members.getByTestId("cohort-member-filter-sync").click()
    await page.getByRole("option", {name: "Needs attention"}).click()

    // Both filters apply: rows matching the term that are also out of step.
    const rows = members.locator("tbody tr")
    await expect(rows).toHaveCount(3)
    await expect(members.getByTestId("cohort-subject-member-301")).toHaveCount(0)
  })

  test("offers each row only the actions that apply to it", async ({page}) => {
    const members = await openMembers(page)

    // A member can be re-evaluated and nothing else.
    await members.getByTestId("cohort-subject-member-menu-301").click()
    await expect(page.getByTestId("cohort-subject-member-reeval-1")).toBeVisible()
    await expect(page.getByTestId("cohort-subject-member-remove-301")).toHaveCount(0)
    await page.keyboard.press("Escape")

    // A row only the target has can be removed from it, and linked when nobody claims it.
    await members.getByTestId("cohort-subject-member-menu-601").click()
    await expect(page.getByTestId("cohort-subject-member-remove-601")).toBeVisible()
    await expect(page.getByTestId("cohort-subject-member-link-601")).toBeVisible()
  })

  test("keeps the targets table, and its menus, inside a narrow viewport", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())
    await page.setViewportSize({width: 412, height: 839})
    await page.goto(SUBJECT)

    const targets = page.getByTestId("cohort-subject-targets")
    await targets.getByTestId("info-box-toggle").first().click()
    // The box expands on a transition, and a table measured mid-expand is not yet its own width.
    await expect(targets.getByTestId("cohort-subject-target-menu-brevo")).toBeVisible()

    // Six columns did not fit a phone: the table scrolled sideways and carried its own action
    // menus off-screen, where the only way to reach them was a scroll nothing advertises.
    // Polled rather than measured once: the box expands on a transition, and the table is not
    // its final width until that has run.
    await expect.poll(async () => targets.locator(".v-table__wrapper").evaluate(
      (el) => el.scrollWidth - el.clientWidth,
    )).toBeLessThanOrEqual(1)

    for (const testid of ["cohort-subject-targets-menu", "cohort-subject-target-menu-brevo"]) {
      const box = (await targets.getByTestId(testid).boundingBox())!
      expect(box.x + box.width).toBeLessThanOrEqual(412)
    }
  })

  test("says when each target was last reconciled, in a column of its own", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())
    await page.goto(SUBJECT)

    const targets = page.getByTestId("cohort-subject-targets")
    await targets.getByTestId("info-box-toggle").first().click()

    // The date is the cell's whole content: the column heading already says what it is.
    await expect(targets.getByTestId("cohort-subject-target-reconciled-brevo"))
      .not.toContainText("last reconciled")

    // Reconciling is one of the target's actions, so it lives with the other two.
    await targets.getByTestId("cohort-subject-target-menu-brevo").click()
    await expect(page.getByTestId("cohort-subject-reconcile-brevo")).toBeVisible()
  })
})

test.describe("cohort subject detail — where a target lives", () => {
  test("says which folder a target sits in, not only what it is called", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())
    await page.goto(COMMITTEE_SUBJECT)

    const targets = page.getByTestId("cohort-subject-targets")
    await targets.getByTestId("info-box-toggle").first().click()

    // The row's first column already names the system, so the path picks up below it: the
    // folder holding the list, then the list.
    const row = targets.getByTestId("cohort-subject-target-brevo")
    const path = row.getByTestId("target-path")
    await expect(path).toContainText("Committees")
    await expect(path.locator(".target-path__leaf")).toHaveText("Web Cmte")
    await expect(path).not.toContainText("Brevo")
  })

  test("tells two same-named lists apart by where each one is filed", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())
    await page.goto(COMMITTEE_SUBJECT)

    const targets = page.getByTestId("cohort-subject-targets")
    await targets.getByTestId("info-box-toggle").first().click()
    await targets.getByTestId("cohort-subject-targets-menu").click()
    await page.getByTestId("cohort-subject-add-target").click()

    // Brevo holds two lists called Web Cmte. Picking the right one is impossible on the name
    // alone, so every option carries its path.
    await page.getByTestId("target-picker-combobox").click()
    const options = page.locator(".v-overlay .v-list-item-title", {hasText: "Web Cmte"})
    await expect(options).toHaveCount(2)
    await expect(options.filter({hasText: "Committees"})).toHaveCount(1)
    await expect(options.filter({hasText: "Archive"})).toHaveCount(1)
  })
})
