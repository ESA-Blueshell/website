import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * The table header is `position: sticky`, and both it and the zebra stripe under it used to
 * mix their tint from black. On the dark theme that darkens a row into the background instead
 * of lifting it out, so the striping was close to invisible and the header hover read as a
 * smudge.
 *
 * These rules paint the desktop table. The narrow layout renders a list of cards instead and
 * has nothing for them to match, so the tests say so rather than asserting against whatever
 * else happens to be on the page.
 */
/** Rows carrying a member, as opposed to the scroller's spacers. */
const dataRow = '[data-testid^="member-manager-row-"]'

async function openDesktopTable(page: import("./test").Page, dark = false): Promise<boolean> {
  await page.setViewportSize({width: 1300, height: 800})
  await installApiMocks(page)
  await loginAsBoard(page.context())
  if (dark) await page.addInitScript(() => localStorage.setItem("esa-blueshell.nl:darkMode", "true"))
  await page.goto("/user-manager")
  await page.getByTestId("member-manager-table").waitFor()

  // Skip on the layout, not on whether rows happen to be visible yet: a probe that can be
  // false for two different reasons turns a real regression into a silent skip.
  const narrow = await page.getByTestId("member-manager-mobile-list").isVisible()
  // A data row, not `tbody tr`: the virtual scroller brackets the rendered window with
  // zero-height spacer rows, and waiting for one of those to become visible never returns.
  if (!narrow) await page.locator(dataRow).first().waitFor()
  return !narrow
}

test.describe("user manager table surfaces", () => {
  test("the sticky header stays opaque while hovered", async ({page}) => {
    test.skip(!(await openDesktopTable(page)), "narrow layout renders cards, not this table")

    const header = page.locator(".sortable-header").first()
    await header.hover()

    const painted = await header.evaluate((el) => {
      const style = getComputedStyle(el)
      return {color: style.backgroundColor, image: style.backgroundImage}
    })

    // The tint is layered over an opaque surface, so the colour behind it has no alpha —
    // otherwise the rows scrolling under a sticky header show through it.
    expect(painted.color).not.toMatch(/rgba\([^)]*,\s*0?\.\d+\s*\)/)
    expect(painted.image).toContain("gradient")
  })

  test("the header hover is tinted with the theme, not with black", async ({page}) => {
    test.skip(!(await openDesktopTable(page, true)), "narrow layout renders cards, not this table")

    const header = page.locator(".sortable-header").first()
    await header.hover()

    // getComputedStyle resolves the custom property, so the assertion is on the colour it
    // resolved to. On the light theme the foreground *is* black and the two are
    // indistinguishable; the dark theme is where the difference is visible and where the
    // old rule was wrong.
    const image = await header.evaluate((el) => getComputedStyle(el).backgroundImage)
    const tint = image.match(/rgba?\(([^)]+)\)/)![1]!.split(",").slice(0, 3).map(Number)
    expect(tint.reduce((sum, channel) => sum + channel, 0)).toBeGreaterThan(0)
  })

  test("the stripe is tinted with the theme, not with black", async ({page}) => {
    test.skip(!(await openDesktopTable(page, true)), "narrow layout renders cards, not this table")

    // The stripe is keyed off the row's index in the list rather than `:nth-child`, which
    // would land on a spacer row here and would follow the window rather than the list.
    const stripe = await page.locator(`${dataRow}.mm-row--odd`).first()
      .evaluate((el) => getComputedStyle(el).backgroundColor)

    // On the dark theme the foreground is light, so a stripe mixed from it is not all zeroes.
    const [r, g, b] = stripe.match(/\d+/g)!.map(Number)
    expect(r + g + b).toBeGreaterThan(0)
  })
})
