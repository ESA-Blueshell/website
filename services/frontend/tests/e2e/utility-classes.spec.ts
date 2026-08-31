import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * Reads what a class actually resolves to on a bare element outside the esports island,
 * which is the only way to tell a class that is defined from one that merely looks defined.
 */
const resolve = (page: import("@playwright/test").Page, cls: string, prop: string) =>
  page.evaluate(([c, p]) => {
    const el = document.createElement("span")
    el.className = c
    el.textContent = "x"
    document.body.appendChild(el)
    const value = getComputedStyle(el).getPropertyValue(p)
    el.remove()
    return value
  }, [cls, prop] as const)

test.describe("utility classes outside the esports island", () => {
  // Both of these were supplied by Tailwind while it was escaping the island, and both went
  // silently unstyled the first time that leak was closed: the managers lost their monospace
  // columns, and the bulk dialogs' warning rows fell back to body colour.
  test("the site's own utilities resolve", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")

    expect(await resolve(page, "font-mono", "font-family")).toContain("mono")
    expect(await resolve(page, "text-warning", "color")).toBe("rgb(255, 176, 32)")
  })

  // The containment itself. `truncate` is Tailwind's and is written nowhere in this codebase,
  // so it must not exist: `source(none)` in styles/island.css is what keeps it from being
  // generated out of the word appearing in a comment or a `text-truncate`. If esports ever
  // writes `truncate` for real, this canary needs swapping for another Tailwind-only name —
  // it is asserting containment, not that the class is forbidden.
  test("a tailwind class nothing writes is not generated", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")

    expect(await resolve(page, "truncate", "text-overflow")).toBe("clip")
  })
})
