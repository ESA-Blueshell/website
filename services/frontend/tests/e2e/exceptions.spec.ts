import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin, loginAsBoard} from "./mocks"

test.describe("Exceptions in Management", () => {
  test("an admin opens an open exception, follows it to its job's trace and marks it resolved", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())
    await page.goto("/management/users")

    await page.getByTestId("management-nav-exceptions").click()
    await expect(page).toHaveURL(/\/management\/exceptions$/)
    await page.getByTestId("exception-row-3").click()

    await expect(page.getByTestId("exception-stacktrace")).toContainText("Sync.kt:4")
    await expect(page.getByTestId("exception-concern-link")).toHaveAttribute("href", "/management/jobs/700")

    await page.getByTestId("exception-resolve").click()
    await expect(page.getByTestId("exception-resolve")).toHaveCount(0)
    await expect(page.getByTestId("exception-facts")).toContainText("Resolved")
  })

  test("the board is turned away", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management/exceptions")

    await expect(page).toHaveURL(/\/unauthorized/)
  })
})
