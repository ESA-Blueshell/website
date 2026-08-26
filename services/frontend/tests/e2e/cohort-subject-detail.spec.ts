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

  test("each section says how much it holds", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    await expect(page.getByTestId("cohort-subject-identity"))
      .toContainText("1 member · 1 sync target")
    await expect(page.getByTestId("cohort-subject-rules")).toContainText("1 rule · 1 enabled")
    await expect(page.getByTestId("cohort-subject-targets")).toContainText("1 sync target")
    // The count is the section's subtitle now, not part of its heading.
    await expect(page.getByTestId("cohort-subject-members")).toContainText("1 member")
    await expect(page.getByTestId("cohort-subject-members")).not.toContainText("Members (1)")
  })

  test("keeps the add-target action on the sync targets header", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)

    const targets = page.getByTestId("cohort-subject-targets")
    await expect(targets.getByTestId("cohort-subject-add-target")).toBeVisible()
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
