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
    // the title as well, and again under every section's heading.
    await expect(page.getByTestId("cohort-subject-member-count")).toContainText("1")
    await expect(page.getByTestId("cohort-subject-rules")).toContainText("1 rule · 1 enabled")
    await expect(page.getByTestId("cohort-subject-targets")).toContainText("1 sync target")
    await expect(page.getByTestId("cohort-subject-members")).toContainText("1 member")
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
