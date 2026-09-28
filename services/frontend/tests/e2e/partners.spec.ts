import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("partners pages", () => {
  test("renders partner routes and validates outbound links", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/partners/become-a-partner")
    await expect(page.getByTestId("partners-hero")).toBeVisible()
    await expect(page.getByRole("link", {name: "external-affairs@blueshell.utwente.nl"})).toHaveAttribute(
      "href",
      "mailto:external-affairs@blueshell.utwente.nl",
    )

    await page.goto("/partners/el-nino")
    await expect(page).toHaveURL(/\/partners\/el-nino$/)
    await expect(page.getByRole("heading", {level: 1})).toContainText(/El Niño/i)
    await expect(page.locator("a[href='https://www.elnino.tech/vacatures']")).toBeAttached()
    await expect(page.locator("a[href='https://www.elnino.tech/getajob']")).toBeAttached()
    await expect(page.locator("a[href='https://wa.me/31626978392']")).toBeAttached()

    await page.goto("/partners/marketing-maatwerk")
    await expect(page.getByRole("heading", {level: 1})).toContainText(/Marketing Maatwerk/i)
    await expect(page.getByRole("link", {name: "Visit Marketing Maatwerk"})).toHaveAttribute(
      "href",
      "https://marketingmaatwerk.nl/",
    )
    await expect(page.getByRole("link", {name: /visit website/i})).toHaveAttribute(
      "href",
      "https://marketingmaatwerk.nl/",
    )
    await expect(page.getByRole("link", {name: /go to contact form/i})).toHaveAttribute(
      "href",
      "https://marketingmaatwerk.nl/contact/",
    )
  })
})
