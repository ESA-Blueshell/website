import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import {eightSeasonFixtures} from "./esportsStrip"

/**
 * Runs in the motion project, which does not emulate reduced motion. The chain lighting up
 * and a slice opening are the whole point of this page, and both are switched off everywhere
 * else by design.
 */
test.describe("the season timeline, with motion", () => {
  test("lights the chain as far as the season under the pointer", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/valorant")
    const timeline = page.getByTestId("esports-season-timeline")
    await timeline.waitFor()

    // How far the chain is lit, as the component itself records it.
    const lit = () => timeline.evaluate(
      el => Number.parseFloat(getComputedStyle(el).getPropertyValue("--lit")),
    )

    // At rest it runs as far as the season on show, which is the newest.
    const atRest = await lit()
    expect(atRest).toBeGreaterThan(50)

    await page.getByTestId("esports-season-node-19").hover()
    await expect.poll(lit).toBeLessThan(atRest)

    await page.getByTestId("esports-season-node-20").hover()
    await expect.poll(lit).toBe(atRest)
  })

  test("highlights the band under the pointer, and bolds the half being read", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/valorant")
    await page.getByTestId("esports-season-timeline").waitFor()

    const band = page.getByTestId("esports-season-node-19")
    const wash = band.locator(".season-band__wash")
    const half = band.locator(".season-band__label--half")
    const before = await wash.evaluate(el => Number.parseFloat(getComputedStyle(el).opacity))

    await band.hover()

    // The whole band lights, not just its node.
    await expect.poll(async () => wash.evaluate(el => Number.parseFloat(getComputedStyle(el).opacity)))
      .toBeGreaterThan(before)
    // And the half under the pointer is the one named in bold.
    const weight = await half.evaluate(el => getComputedStyle(el).fontWeight)
    expect(Number(weight)).toBeGreaterThanOrEqual(700)
  })

  test("takes a click to change season, not a hover", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/valorant")
    await page.getByTestId("esports-season-timeline").waitFor()

    const older = page.getByTestId("esports-season-node-19")
    await older.hover()

    // Crossing the strip must not drag the page from season to season.
    await expect(page).not.toHaveURL(/season=19/)

    await older.click()

    await expect(page).toHaveURL(/season=19/)
  })

  test("travels while the pointer rests on the side of the strip", async ({page}) => {
    await installApiMocks(page, eightSeasonFixtures)
    await loginAsBoard(page.context())
    await page.goto("/esports/valorant")
    const strip = page.getByTestId("esports-season-timeline")
    await strip.waitFor()
    await expect(page.getByTestId("esports-season-pan-back")).toBeVisible()

    const at = () => page.locator(".season-strip__scroll").evaluate(el => el.scrollLeft)
    const before = await at()
    expect(before).toBeGreaterThan(0)

    // Anywhere down that side, not the chevron itself: the whole edge answers the pointer.
    const box = (await strip.boundingBox())!
    await page.mouse.move(box.x + 20, box.y + box.height - 14)

    await expect.poll(at).toBeLessThan(before)
    // And once it has travelled, the way on appears.
    await expect(page.getByTestId("esports-season-pan-on")).toBeVisible()

    // Off the strip, and it stands still again.
    await page.mouse.move(box.x + box.width / 2, box.y + box.height + 120)
    const parked = await at()
    await expect.poll(at).toBe(parked)
  })

  test("opens the slice under the pointer and closes the one that was open", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/valorant")
    const first = page.getByTestId("team-roster-1")
    const second = page.getByTestId("team-roster-2")
    await first.waitFor()

    // The first opens itself once the page has settled, so the animation is seen happening.
    await expect.poll(async () => first.getAttribute("class")).toContain("team-slice--open")

    await second.hover()

    await expect.poll(async () => second.getAttribute("class")).toContain("team-slice--open")
    await expect.poll(async () => first.getAttribute("class")).not.toContain("team-slice--open")
  })
})
