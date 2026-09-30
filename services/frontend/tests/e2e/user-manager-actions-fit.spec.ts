import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin, loginAsBoard} from "./mocks"

/**
 * The table lays its columns out fixed, so a column narrower than its content does not grow:
 * the content spills over the cell's edge and the last action is cut off by the card. An admin
 * gets two more actions than the rest of the board, so both are checked, at the narrowest
 * width that shows the table and at the widest the card reaches. The bulk menu in the header
 * closes the column on the same edge as the row's last action.
 */
const dataRow = '[data-testid^="member-manager-row-"]'

for (const [who, login] of [["an admin", loginAsAdmin], ["a board member", loginAsBoard]] as const) {
  for (const width of [1145, 1300, 1920]) {
    test(`every action fits its cell for ${who} at ${width}px`, async ({page}) => {
      await page.setViewportSize({width, height: 800})
      await installApiMocks(page)
      await login(page.context())
      await page.goto("/user-manager")
      const row = page.locator(dataRow).first()
      await row.waitFor()

      const fit = await row.evaluate((tr) => {
        const cell = tr.lastElementChild as HTMLElement
        const cellBox = cell.getBoundingClientRect()
        const buttons = [...cell.querySelectorAll("button")].map((b) => b.getBoundingClientRect())
        const wrapper = tr.closest(".v-table__wrapper") as HTMLElement
        return {
          buttons: buttons.length,
          spill: Math.max(...buttons.map((b) => b.right)) - cellBox.right,
          sideways: wrapper.scrollWidth - wrapper.clientWidth,
          menu: document.querySelector('[data-testid="bulk-actions-menu-btn"]')!.getBoundingClientRect().right
            - buttons.at(-1)!.right,
        }
      })

      expect(fit.buttons).toBe(who === "an admin" ? 6 : 4)
      expect(fit.spill).toBeLessThanOrEqual(0)
      expect(fit.sideways).toBe(0)
      expect(Math.abs(fit.menu)).toBeLessThan(1)
    })
  }
}
