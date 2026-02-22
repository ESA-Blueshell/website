import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("blogs pages", () => {
  test("renders blog list and navigates to selected blog details", async ({page}) => {
    await installApiMocks(page, {
      blogs: [
        {
          id: 42,
          title: "Community Wrap-up",
          publishedAt: "2025-01-10T12:00:00.000Z",
          html: "<h1>Community Wrap-up</h1><p>Highlights from this month.</p>",
        },
        {
          id: 43,
          title: "Events Preview",
          publishedAt: "2025-01-15T12:00:00.000Z",
          html: "<h1>Events Preview</h1><p>What is coming next.</p>",
        },
      ],
    })

    await page.goto("/blogs")

    await expect(page.getByText("NEWSLETTERS", {exact: true})).toBeVisible()
    await expect(page.getByText("Community Wrap-up", {exact: true})).toBeVisible()
    await expect(page.getByText("Events Preview", {exact: true})).toBeVisible()

    await page.getByText("Community Wrap-up", {exact: true}).click()

    await expect(page).toHaveURL(/\/blogs\/42$/)
    const blogFrame = page.locator("iframe[title='Blog content']")
    await expect(blogFrame).toBeVisible()
    await expect(blogFrame).toHaveAttribute("srcdoc", /Community Wrap-up/)
  })

  test("shows a not-found state for unknown blog ids", async ({page}) => {
    await installApiMocks(page, {blogs: []})

    await page.goto("/blogs/999")

    await expect(page.getByText("Blog not found.", {exact: true})).toBeVisible()
  })

  test("shows a load failure state when blog request fails", async ({page}) => {
    await installApiMocks(page, {
      blogStatusById: {"500": 500},
    })

    await page.goto("/blogs/500")

    await expect(page.getByText("Failed to load blog.", {exact: true})).toBeVisible()
  })
})
