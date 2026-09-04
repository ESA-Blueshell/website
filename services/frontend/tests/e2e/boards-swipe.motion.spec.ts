import {devices, type Locator} from "@playwright/test"
import type {Page} from "./test"
import {expect, test} from "./test"
import {dragBand, standing} from "./bandSwipe"
import {aimedAt, arrivedOpen, framesOf} from "./sliceBand"
import {recordScrolls, SCROLLER, scrolled, scrollsAsked, sixBoards} from "./boardLine"
import {installApiMocks} from "./mocks"

/**
 * The choreography of a drag: the band under the finger, the handover on release, the spring
 * home, the resistance at the ends of the line, and the strip travelling alongside.
 *
 * Runs only in the motion project, which is the one project that does not emulate reduced motion
 * — and that project runs a desktop device, so this file overrides its own context with a phone's.
 * Those are per-file context options, so the desktop motion specs beside it are untouched and no
 * fifth project is added for them: eight workers already share four vCPUs. Stated for the file
 * rather than inside the describe below because the browser to launch is a worker's business, and
 * a describe that changed it would force a worker of its own.
 *
 * What is asserted here rather than in the ordinary board spec is everything that happens once
 * the finger has left the glass, because that is exactly what reduced motion clamps. The band
 * following the finger is asserted over there, where every project can see it.
 */

test.use({...devices["Pixel 7"]})

const SWIPE = "[data-testid=\"board-swipe\"]"
const CARRIED = `${SWIPE} .band-swipe__carried`
const ASIDE = `${SWIPE} > .band-swipe__aside`

/**
 * The same line, with something written about the second and third member of every board.
 *
 * Nothing is written about the line's own members, so no slice on it opens onto anything and a
 * band arriving open would have nothing to show for it. Not the first member: the slice that
 * opens is the first that opens onto anything rather than the first drawn. And two of them, so
 * the one left shut is a slice of the same shape to measure the open one against.
 */
const written = sixBoards.map(board => ({
  ...board,
  members: board.members.map((member, at) => ({
    ...member,
    description: at === 1 || at === 2
      ? "Keeping the books, and explaining once a year where the money actually went."
      : null,
  })),
}))

/** Under this much, the band calls a difference in height a rounding error and does not carry it. */
const HAIR = 8

/** Where the band stands once no height is held on it at all, which is the end of the pass. */
const standsAt = (page: Page) => page.waitForFunction(() => {
  const shell = document.querySelector("[data-testid=\"board-swipe\"]") as HTMLElement | null
  if (!shell || shell.style.height) return null
  return Math.round(shell.getBoundingClientRect().height)
}).then(handle => handle.jsonValue())

/** Sampled rather than polled to a figure: the claim is that nothing moved, not where it ended. */
const unmoved = async (page: Page, band: Locator, height: number) => {
  for (let sample = 0; sample < 6; sample += 1) {
    expect(Math.round((await band.boundingBox())!.height)).toBe(height)
    await page.waitForTimeout(25)
  }
}

test.describe("dragging the board page", () => {
  test("carries the band and the board beside it under the finger", async ({page}) => {
    await installApiMocks(page, {boards: sixBoards})
    await page.goto("/board?board=3")
    await expect(page.getByTestId("board-band-name")).toHaveText("Drieden")

    const band = page.getByTestId("board-swipe")
    const width = (await band.boundingBox())!.width
    await dragBand(page, band, {by: 60, release: false})

    // The band stands where the finger left it, to within a pixel of rounding, because that is
    // the whole of what direct manipulation means.
    const [carried] = await standing(page, CARRIED)
    expect(carried).toBeGreaterThan(50)
    expect(carried).toBeLessThan(70)

    // And the board it is heading for stands exactly a width to the left of it, drawing the real
    // thing rather than a box that turns into it: the name on it is the earlier board's.
    const [aside] = await standing(page, ASIDE)
    expect(aside).toBeCloseTo(carried! - width, 0)
    await expect(page.locator(ASIDE)).toContainText("Tweeden")

    await page.mouse.up()
  })

  test("springs the band home when the release was neither far enough nor fast enough", async ({page}) => {
    await installApiMocks(page, {boards: sixBoards})
    await page.goto("/board?board=3")
    const band = page.getByTestId("board-swipe")

    await dragBand(page, band, {by: 60, release: false})
    expect((await standing(page, CARRIED))[0]).toBeGreaterThan(50)
    await page.mouse.up()

    // Home, and the board it was heading for taken off the page with the gesture.
    await expect.poll(async () => Math.round((await standing(page, CARRIED))[0] ?? -1)).toBe(0)
    await expect(page.locator(ASIDE)).toHaveCount(0)
    await expect(page).toHaveURL(/\?board=3$/)
    await expect(page.getByTestId("board-band-name")).toHaveText("Drieden")
  })

  test("eases the board it was heading for onto the screen and only then asks the page", async ({page}) => {
    await installApiMocks(page, {boards: sixBoards})
    await page.goto("/board?board=3")
    const band = page.getByTestId("board-swipe")

    await dragBand(page, band, {by: 260, release: false})
    const [held] = await standing(page, ASIDE)
    await page.mouse.up()

    // A fifth of the way into the ease. The gesture finishes its own pass before the page is
    // asked for anything: the board it was heading for is still travelling, and the url still
    // names the board the finger started on.
    await page.waitForTimeout(150)
    expect(new URL(page.url()).searchParams.get("board")).toBe("3")
    const [coming] = await standing(page, ASIDE)
    expect(coming).toBeGreaterThan(held!)
    expect(coming).toBeLessThan(0)

    await expect(page).toHaveURL(/\?board=2$/)
    await expect(page.getByTestId("board-band-name")).toHaveText("Tweeden")
  })

  test("does not send the arrived board across the screen a second time", async ({page}) => {
    await installApiMocks(page, {boards: sixBoards})
    await page.goto("/board?board=3")
    const band = page.getByTestId("board-swipe")

    await dragBand(page, band, {by: 260})
    await expect(page).toHaveURL(/\?board=2$/)

    // The direction is still reported, so the pass a click on a node plays would have played
    // here, sending the same contents across the screen a second time having just been dragged
    // across it. Sampled rather than polled to a value, because the claim is that nothing moved
    // at any point rather than anything about where it ended up.
    await expect(band).toHaveAttribute("data-swipe", "past")
    for (let sample = 0; sample < 8; sample += 1) {
      const panels = await standing(page, `${SWIPE} > *`)
      expect(panels.every(panel => Math.abs(panel) < 1), `panels at ${panels.join(", ")}`).toBe(true)
      await page.waitForTimeout(25)
    }
    await expect(page.locator(ASIDE)).toHaveCount(0)
  })

  test("lands with the arriving board's slice open, and grows nothing after the pass", async ({page}) => {
    await installApiMocks(page, {boards: written})
    await page.goto("/board?board=3")
    await expect(page.getByTestId("board-band-name")).toHaveText("Drieden")

    // The second member of the board the drag is heading for, which is the slice it arrives with
    // open. Watched from before the release, because that is where the frames are.
    const band = page.getByTestId("board-swipe")
    await dragBand(page, band, {by: 260, release: false})
    const watching = framesOf(page, "board-member-21")
    await page.mouse.up()
    const seen = await watching

    await expect(page).toHaveURL(/\?board=2$/)

    // Open in the frame it was first drawn in and in every frame after, at one height the whole
    // way: the swipe was the animation, and nothing grows once the finger has left the glass.
    arrivedOpen(seen)

    // And it is genuinely open rather than there being no room to grow into: as much is written
    // about the member beside them, whose slice is shut and shorter by the room those words take.
    const shut = (await page.getByTestId("board-member-22").boundingBox())!
    expect(seen[0]!.height).toBeGreaterThan(shut.height + 10)
  })

  test("aims the pass at the height the band it brings in stands at, on a swipe and on a hit", async ({page}) => {
    await installApiMocks(page, {boards: written})
    await page.goto("/board?board=3")
    await expect(page.getByTestId("board-band-name")).toHaveText("Drieden")

    const band = page.getByTestId("board-swipe")
    const from = Math.round((await band.boundingBox())!.height)

    // The pass a finger plays measures the board it is dragging in, which is drawn open — the
    // axis is claimed before it is mounted — so the figure it aims at is the finished layout.
    await dragBand(page, band, {by: 260})
    const swiped = await aimedAt(page, SWIPE)
    await expect(page).toHaveURL(/\?board=2$/)

    // Aimed at a real difference rather than at the height it already stood at, which is what
    // makes the two claims below claims about anything.
    expect(Math.abs(swiped - from)).toBeGreaterThan(HAIR)
    // And it is the height the band stands at once nothing is held any more: the swipe ends on
    // the finished band, so nothing resizes after the finger has gone.
    expect(await standsAt(page)).toBe(swiped)
    await unmoved(page, band, swiped)

    // The pass the band plays for itself, a stop arriving without a gesture, aims at the same
    // thing: it measures the arriving board a frame after it was built, and it was built open.
    await page.getByTestId("board-node-5").click()
    const hit = await aimedAt(page, SWIPE)
    await expect(page.getByTestId("board-band-name")).toHaveText("Eeveelutions")

    expect(Math.abs(hit - swiped)).toBeGreaterThan(HAIR)
    expect(await standsAt(page)).toBe(hit)
    await unmoved(page, band, hit)
  })

  test("leans and springs home at the end of the line, where there is no board that way", async ({page}) => {
    await installApiMocks(page, {boards: sixBoards})
    await page.goto("/board?board=1")
    const band = page.getByTestId("board-swipe")

    // The oldest board recorded, hauled six times further back than the lean allows.
    await dragBand(page, band, {by: 240, release: false})

    const [leaning] = await standing(page, CARRIED)
    // A third of the travel would be 80; the cap is 2.5rem, which is 40 at the browser's default.
    expect(leaning).toBeGreaterThan(28)
    expect(leaning).toBeLessThan(46)
    // Nothing is drawn beside it, because there is nothing that way to draw.
    await expect(page.locator(ASIDE)).toHaveCount(0)

    await page.mouse.up()
    await expect.poll(async () => Math.round((await standing(page, CARRIED))[0] ?? -1)).toBe(0)
    await expect(page).toHaveURL(/\?board=1$/)
  })

  test("travels the line to a board a finger arrived at, and jumps to one a node was hit for", async ({page}) => {
    await recordScrolls(page)
    await installApiMocks(page, {boards: sixBoards})
    // The fourth and third boards, because both of their nodes can actually reach the middle of
    // this window: a line only a screenful and a half long cannot centre the stops at its ends,
    // and a scroll clamped at nought would prove nothing either way.
    await page.goto("/board?board=4")
    await page.getByTestId("board-node-4").waitFor()
    const opened = await scrolled(page)

    await dragBand(page, page.getByTestId("board-swipe"), {by: 260})
    await expect(page).toHaveURL(/\?board=3$/)

    // The line travels alongside the band rather than snapping ahead of it, and ends with the
    // arrived board's node in the middle of the window.
    await expect.poll(async () => (await scrollsAsked(page)).at(-1)).toBe("smooth")
    await expect.poll(async () => Math.round(await scrolled(page))).not.toBe(Math.round(opened))
    const node = (await page.getByTestId("board-node-3").boundingBox())!
    const box = (await page.locator(SCROLLER).boundingBox())!
    expect(Math.abs((node.x + node.width / 2) - (box.x + box.width / 2))).toBeLessThan(24)

    // A hit on one of the strip's own nodes is what it always was: the line does not move at
    // all, because the node the visitor aimed at would slide out from under their finger.
    const asked = (await scrollsAsked(page)).length
    await page.getByTestId("board-node-5").click()
    await expect(page).toHaveURL(/\?board=5$/)
    await expect(page.getByTestId("board-band-name")).toHaveText("Eeveelutions")
    expect(await scrollsAsked(page)).toHaveLength(asked)

    // And a board arriving from somewhere the visitor cannot see is centred instantly, which is
    // also what spends the mark the gesture left: the third board was travelled to once, and is
    // not travelled to again just because the back button names it.
    await page.goBack()
    await expect(page).toHaveURL(/\?board=3$/)
    await expect.poll(async () => (await scrollsAsked(page)).at(-1)).toBe("auto")
  })

  /**
   * What a visitor who asked for reduced motion gets, which is the gesture without its tails.
   *
   * The preference is emulated for this test rather than taken from the project, because
   * `use.reducedMotion` does not reach the page on Playwright 1.60 — #852 — so every
   * "deterministic" project in this suite is in fact running with full motion. The same line and
   * the same reasoning are in `esports-season-on-show.spec.ts`; when #852 is fixed, both go.
   *
   * The durations are read off the animations themselves rather than timed with a clock. Eight
   * workers share four vCPUs here, so a wall-clock measurement of "it settled quickly" is a
   * measurement of the runner's mood; what the preference actually changes is the duration the
   * band asks for, and that is a number the page will state.
   */
  test("still follows the finger under reduced motion, and lands without the long ease", async ({page}) => {
    await page.emulateMedia({reducedMotion: "reduce"})
    await recordScrolls(page)
    await installApiMocks(page, {boards: sixBoards})
    await page.goto("/board?board=4")
    await page.getByTestId("board-node-4").waitFor()

    // Held, not released: content moving under a finger is not the unbidden movement the
    // preference is about, so this half of the gesture survives it. A band that refused to
    // follow would leave a visitor who asked for less movement with no gesture at all.
    await dragBand(page, page.getByTestId("board-swipe"), {by: 260, release: false})
    expect(Math.round((await standing(page, CARRIED))[0] ?? 0)).toBeGreaterThan(80)

    await page.mouse.up()

    // The tails are what gets clamped: the island's ceiling is 120ms against the 850ms the
    // gesture would otherwise take, so nothing crosses the window under its own steam.
    //
    // Only the gesture's own animations, which are the two panels it slides and the shell whose
    // height it carries between them. Every animation on the page is the wrong question: the
    // band is full of slices with transitions of their own, and one of those answered 500ms and
    // failed a claim about the gesture that the gesture was keeping.
    const asked = await page.evaluate(([swipe, carried, aside]) => {
      const mine = [
        document.querySelector(swipe),
        ...document.querySelectorAll(carried),
        ...document.querySelectorAll(aside),
      ].filter((el): el is Element => el != null)
      return document.getAnimations()
        .filter(one => mine.includes((one.effect as KeyframeEffect | null)?.target as Element))
        .map(one => one.effect?.getTiming().duration)
        .filter((ms): ms is number => typeof ms === "number" && ms > 0)
    }, [SWIPE, CARRIED, ASIDE] as const)
    expect(asked.length).toBeGreaterThan(0)
    for (const ms of asked) expect(ms).toBeLessThanOrEqual(200)

    // And the line does not travel either: it is put where it belongs in one step.
    await expect(page).toHaveURL(/\?board=3$/)
    await expect.poll(async () => (await scrollsAsked(page)).at(-1)).toBe("auto")

    // The end state is the one a visitor without the preference reaches: same board, band home.
    await expect(page.getByTestId("board-band-name")).toHaveText("Drieden")
    await expect.poll(async () => Math.round((await standing(page, CARRIED))[0] ?? -1)).toBe(0)
    await expect(page.locator(ASIDE)).toHaveCount(0)
  })
})
