import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

// Subject 102 in the mocks is the committee cohort: one member, one Brevo mapping, one
// enabled rule — enough for every section to have something to count.
const COMMITTEE_SUBJECT = "/management/cohorts/subjects/102"

test.describe("cohort subject detail", () => {
  test("renders every section as a manager card", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    await expect(page.getByTestId("cohort-subject-identity")).toBeVisible()
    await expect(page.getByTestId("cohort-subject-rules")).toBeVisible()
    await expect(page.getByTestId("cohort-subject-targets")).toBeVisible()
    await expect(page.getByTestId("cohort-subject-members")).toBeVisible()
  })

  test("names the cohort kind the way the category page does", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    // Not `committee_members` lower-cased, which is what the page used to derive by hand.
    await expect(page.getByTestId("cohort-subject-identity"))
      .toContainText("Committees · Committee members")
    await expect(page.getByTestId("cohort-subject-identity")).toContainText("Web Cmte")
  })

  test("each box says how much it holds without being opened", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    // The page's own count is the badge on its heading, once — it used to be a line under
    // the title as well, and again under every section's heading. It counts members, so the
    // two rows the target holds and we do not are not in it.
    await expect(page.getByTestId("cohort-subject-member-count")).toContainText("2")
    await expect(page.getByTestId("cohort-subject-rules")).toContainText("1 rule · 1 enabled")
    await expect(page.getByTestId("cohort-subject-targets")).toContainText("1 sync target")
    await expect(page.getByTestId("cohort-subject-members")).toContainText("2 members")
  })

  test("opens a box to show what it holds, and keeps it shut until asked", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    const rules = page.getByTestId("cohort-subject-rules")
    await expect(rules.getByTestId("cohort-subject-rule-9")).toHaveCount(0)

    await rules.getByTestId("info-box-toggle").click()

    // The fact reads as a sentence rather than as the enum it is stored under.
    await expect(rules.getByTestId("cohort-subject-rule-9")).toContainText("Committee")
    await expect(rules.getByTestId("cohort-subject-rule-9")).toContainText("42")
  })

  test("keeps the add-target action on the page's own header", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    // Page-level rather than inside the targets box: it adds a target to the cohort, and the
    // page's actions live in one place instead of being scattered over four cards.
    const card = page.getByTestId("cohort-subject-identity")
    await expect(card.getByTestId("cohort-subject-add-target")).toBeVisible()
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

  test("counts members in the badge and names the exceptions beside it", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())
    await page.goto(SUBJECT)

    // Two of the four rows are members; the badge counts those, as the category page counts.
    await expect(page.getByTestId("cohort-subject-member-count")).toContainText("2")
    await expect(page.getByTestId("cohort-subject-members"))
      .toContainText("2 members · 1 not synced · 2 only external")
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

  test("says when the target was last reconciled, beside the button that reconciles it", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())
    await page.goto(SUBJECT)

    const targets = page.getByTestId("cohort-subject-targets")
    await targets.getByTestId("info-box-toggle").first().click()

    await expect(targets.getByTestId("cohort-subject-target-reconciled-brevo"))
      .toContainText("last reconciled")
    await expect(targets.getByTestId("cohort-subject-reconcile-brevo")).toBeVisible()
  })
})
