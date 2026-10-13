import {expect, test, type Page} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"
import {aJob} from "./records"

const PHONE = {width: 390, height: 844}

// The Management table on its first page, Jobs, which reads 50 rows at a time, newest id first.
const jobs = (count: number, over: (id: number) => Parameters<typeof aJob>[0] = () => ({})) =>
  Array.from({length: count}, (_, index) => aJob({id: index + 1, jobType: "email.send", status: "SUCCESS", attempts: 1, ...over(index + 1)}))

const asksForPage = (page: Page, number: number) =>
  page.waitForRequest((request) => new URL(request.url()).pathname.endsWith("/management/jobs") && request.url().includes(`page=${number}`))

const open = async (page: Page, list: ReturnType<typeof jobs>) => {
  await installApiMocks(page, {jobs: list})
  await loginAsAdmin(page.context())
  await page.goto("/management/jobs")
}

const toTheEnd = (page: Page) =>
  page.locator(".mg-table__scroll").evaluate((box) => box.scrollTo(0, box.scrollHeight))

test.describe("the Management table", () => {
  test("reads on as a phone scrolls to the end of its rows, and draws only the rows near the window", {tag: "@phone"}, async ({page}) => {
    await page.setViewportSize(PHONE)
    await open(page, jobs(170))
    await expect(page.getByTestId("job-row-170")).toBeVisible()

    for (const next of [1, 2]) {
      const asked = asksForPage(page, next)
      await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
      await asked
      await expect(page.getByTestId("job-manager-table")).toContainText(`1-${(next + 1) * 50} of 170`)
    }
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))

    await expect(page.getByTestId("job-row-21")).toBeVisible()
    expect(await page.locator(".mg-row").count()).toBeLessThan(80)
  })

  test("reads on by itself when the first rows leave room in the window", async ({page}) => {
    await page.setViewportSize({width: 1280, height: 4200})
    const asked = asksForPage(page, 1)

    await open(page, jobs(120))

    await asked
  })

  test("keeps its columns where they are as a search narrows the rows", async ({page}) => {
    await open(page, jobs(40, (id) => (id === 7
      ? {jobType: "contact.sync-all", payload: {}}
      : {status: "FAILED", errorMessage: `Brevo refused contact ${id} because the list it belongs to is gone`, payload: {userId: id, reason: "nightly"}})))
    const heads = page.locator('[data-testid="job-manager-table"] thead tr:first-child th')
    const lefts = () => heads.evaluateAll((all) => all.map((one) => Math.round(one.getBoundingClientRect().left)))
    await expect(page.getByTestId("job-row-40")).toBeVisible()
    const before = await lefts()

    await page.getByTestId("job-filter-search").fill("contact.sync-all")
    await expect(page.locator("tr[data-row]")).toHaveCount(1)

    const moved = (await lefts()).map((left, index) => Math.abs(left - before[index]))
    expect(Math.max(...moved)).toBeLessThanOrEqual(1)
  })

  test("keeps every act of a later row inside its cell", async ({page}) => {
    await open(page, jobs(120, (id) => (id > 40 ? {} : {status: "FAILED", errorMessage: "Brevo said no"})))
    await expect(page.getByTestId("job-row-120")).toBeVisible()
    const asked = asksForPage(page, 1)
    await toTheEnd(page)
    await asked
    await expect(page.getByTestId("job-manager-table")).toContainText("1-100 of 120")
    await toTheEnd(page)

    const retry = page.getByTestId("job-retry-btn-30")
    await expect(retry).toBeVisible()
    const inside = await retry.evaluate((button) => {
      const cell = button.closest("td")!.getBoundingClientRect()
      const act = button.getBoundingClientRect()
      return act.left >= cell.left && act.right <= cell.right
    })
    expect(inside).toBe(true)
  })

  test("opens a row's page in another tab when it is pressed with the key that asks for one", async ({page, context}) => {
    await open(page, jobs(5))
    const another = context.waitForEvent("page")

    await page.getByTestId("job-row-5").locator("td").first().click({modifiers: ["ControlOrMeta"]})

    expect(new URL((await another).url()).pathname).toBe("/management/jobs/5")
    expect(new URL(page.url()).pathname).toBe("/management/jobs")
  })

  test("draws rows to the end of a window made taller", async ({page}) => {
    await page.setViewportSize({width: 1280, height: 600})
    await open(page, jobs(120))
    await expect(page.getByTestId("job-row-120")).toBeVisible()
    const asked = asksForPage(page, 1)
    await toTheEnd(page)
    await asked
    await expect(page.getByTestId("job-manager-table")).toContainText("1-100 of 120")
    await page.locator(".mg-table__scroll").evaluate((box) => box.scrollTo(0, 0))

    await page.setViewportSize({width: 1280, height: 2000})

    await expect.poll(() => page.locator(".mg-table__scroll").evaluate((box) => {
      const rows = [...box.querySelectorAll("tr[data-row]")]
      return rows[rows.length - 1].getBoundingClientRect().bottom >= box.getBoundingClientRect().bottom - 1
    })).toBe(true)
  })
})
