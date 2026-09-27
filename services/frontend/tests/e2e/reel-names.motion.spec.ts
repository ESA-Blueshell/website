import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * A reel slice's tick and name keep to one path while the belt drifts: they rise and shrink with
 * the slice's opening and never leap. A name re-wrapping as the slice narrows, or the notes under
 * it fading in, used to move the whole title by a line in a single frame.
 *
 * Watched in the motion project, where the belt drifts on its own. A frame is judged only against
 * the slice's previous frame at nearly the same opening, so a frame the machine dropped is not
 * taken for a leap.
 */
test("a reel's names follow their slices without leaping", async ({page}) => {
  await installApiMocks(page)
  await page.goto("/casual")
  await expect(page.locator(".flick-reel__slice").first()).toBeVisible()

  const leaps = await page.evaluate(() => new Promise<string[]>(resolve => {
    const last = new Map<Element, {open: number; y: number}>()
    const found: string[] = []
    const start = performance.now()
    const frame = (now: number) => {
      for (const slice of document.querySelectorAll<HTMLElement>(".flick-reel__slice")) {
        if (getComputedStyle(slice).visibility === "hidden") continue
        const open = Number(slice.style.getPropertyValue("--open") || 0)
        const tick = slice.querySelector(".flick-reel__tick")
        if (!tick) continue
        const y = tick.getBoundingClientRect().top - slice.getBoundingClientRect().top
        const was = last.get(slice)
        if (was && Math.abs(open - was.open) < 0.05 && Math.abs(y - was.y) > 6) {
          found.push(`${slice.dataset.testid} at ${open.toFixed(2)}: ${(y - was.y).toFixed(1)}px`)
        }
        last.set(slice, {open, y})
      }
      if (now - start < 4000) requestAnimationFrame(frame)
      else resolve(found)
    }
    requestAnimationFrame(frame)
  }))

  expect(leaps).toEqual([])
})
