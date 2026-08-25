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

  test("counts read as English on the category page that links here", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/cohorts/committees")

    const group = page.getByTestId("cohort-type-group-COMMITTEE_MEMBERS")
    // Was `Committee members · 1 · 1 members`: a bare number, then a plural for one thing.
    await expect(group).toContainText("Committee members · 1 cohort · 1 member")
    await expect(page.getByTestId("cohort-subject-row-102"))
      .toContainText("1 member · 1 sync target")
  })

  test("goes back to the category the cohort belongs to", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto(COMMITTEE_SUBJECT)
    await page.getByTestId("cohort-subject-back").click()

    await expect(page).toHaveURL(/\/management\/cohorts\/committees$/)
  })
})
