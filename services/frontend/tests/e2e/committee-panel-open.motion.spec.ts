import type {Page} from "@playwright/test"
import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * What "the edit panel is open" has to mean before anything inside it may be clicked.
 *
 * `v-expand-transition` shows the panel at about two pixels and grows it for some 280ms. The
 * submit button is below the fold and travels some 460px while that runs, and partway down it
 * holds one bounding box long enough for Playwright to call it stable — two matching frames is
 * all that is asked. The click point is computed where the button was, the panel carries on, and
 * the event is delivered to the form container that has arrived at those coordinates. `save` is
 * never entered, so nothing is refused and no request is made: #1042, a system-test flake of
 * roughly one run in a hundred and eighty.
 *
 * `CommitteeManagerHelper.openEditForm` waits for the panel's height to stop changing rather than
 * for the panel to appear. These two cases are the halves of why: a point taken while the panel
 * is still opening is worthless, and a point taken once its height has settled is good.
 *
 * The e2e default is `reducedMotion: "reduce"`, under which the panel is at full height on the
 * first frame and none of this exists — hence the motion project.
 */
const COMMITTEES = Array.from({length: 30}, (_, index) => ({
  id: 900 + index,
  name: `Committee ${index}`,
  description: `Description number ${index}, long enough to satisfy the rule.`,
  version: 3,
  members: [{userId: 1, role: "Chair"}],
}))

/** Far enough down the list that the panel opens below the fold, as it does in the system test. */
const TARGET = 915
const PANEL = `[data-testid='committee-manager-edit-form-${TARGET}']`
const SUBMIT = "[data-testid='committee-form-submit-btn']"

/** How far along its own time the opening is read: early, where the ease has most left to run. */
const ALONG = 0.1

/** A click point, and whether the submit button is under it. */
interface Aim {
  x: number
  y: number
  hitsButton: boolean
}

/**
 * Holds the panel [ALONG] of the way through opening, and hands back the release.
 *
 * Held at a point along the transition rather than at a delivered frame: whether a sampler lands
 * inside a 280ms movement on a loaded machine is a question about which frames
 * `requestAnimationFrame` happened to deliver, and the middle is the whole of what this asserts.
 * Caught at `transitionrun`, before the first frame is painted. Released, the panel settles where
 * it would have settled anyway.
 */
async function heldPartwayOpen(page: Page): Promise<() => Promise<void>> {
  const holding = page.evaluate(([panel, along]) => new Promise<void>((resolve) => {
    const caught = (event: Event) => {
      const el = event.target as HTMLElement
      if (!el.matches?.(panel)) return
      document.removeEventListener("transitionrun", caught, true)
      const running = el.getAnimations()
      for (const animation of running) {
        animation.pause()
        const timing = animation.effect!.getComputedTiming()
        animation.currentTime = Number(timing.delay ?? 0) + Number(timing.activeDuration ?? 0) * along
      }
      ;(window as unknown as {__held: Animation[]}).__held = running
      resolve()
    }
    document.addEventListener("transitionrun", caught, true)
  }), [PANEL, ALONG] as const)

  await page.getByTestId(`committee-edit-btn-${TARGET}`).click()
  await holding
  return () => page.evaluate(() => {
    (window as unknown as {__held: Animation[]}).__held.forEach((animation) => animation.play())
  })
}

/**
 * Where a click on the submit button would land, aimed the way Playwright aims one: the target
 * scrolled into view and its centre taken, which is the whole of what an action does first.
 */
async function aimAtSubmit(page: Page): Promise<Aim> {
  return page.evaluate((submit) => {
    const button = document.querySelector(submit)!
    button.scrollIntoView({block: "nearest"})
    const box = button.getBoundingClientRect()
    const x = box.x + box.width / 2
    const y = box.y + box.height / 2
    return {x, y, hitsButton: !!document.elementFromPoint(x, y)?.closest(submit)}
  }, SUBMIT)
}

/** The wait the helper makes: the same non-zero height for three frames running. */
async function panelSettled(page: Page) {
  await page.waitForFunction((selector) => {
    const el = document.querySelector(selector) as (HTMLElement & {
      __settledHeight?: number
      __settledFrames?: number
    }) | null
    if (!el) return false
    const height = el.getBoundingClientRect().height
    el.__settledFrames = height > 0 && el.__settledHeight === height ? (el.__settledFrames ?? 0) + 1 : 0
    el.__settledHeight = height
    return el.__settledFrames >= 2
  }, PANEL)
}

async function whatIsUnder(page: Page, aim: Aim): Promise<string> {
  return page.evaluate(([submit, x, y]) => {
    const at = document.elementFromPoint(x as number, y as number)
    if (!at) return "nothing"
    return at.closest(submit as string) ? "the submit button" : `${at.tagName.toLowerCase()}, not the button`
  }, [SUBMIT, aim.x, aim.y] as const)
}

test.use({viewport: {width: 1600, height: 900}})

test.describe("a committee's edit panel opening", () => {
  test.beforeEach(async ({page}) => {
    await installApiMocks(page, {committees: COMMITTEES})
    await loginAsBoard(page.context())
    await page.goto("/committees/manage")
    await page.getByTestId(`committee-row-${TARGET}`).waitFor()
  })

  test("carries the submit button away from a click aimed before it has settled", async ({page}) => {
    const release = await heldPartwayOpen(page)
    const partway = await aimAtSubmit(page)
    await release()
    await panelSettled(page)
    const settled = await aimAtSubmit(page)

    // Hundreds of pixels apart, and what the earlier point now has under it is the form container
    // the panel has grown over it. A click aimed there is delivered to that container, `save` is
    // never entered, and the save the test is waiting for is never attempted.
    expect(Math.abs(settled.y - partway.y)).toBeGreaterThan(100)
    expect(await whatIsUnder(page, partway)).not.toBe("the submit button")
  })

  test("holds the submit button still for a click aimed once it has settled", async ({page}) => {
    await page.getByTestId(`committee-edit-btn-${TARGET}`).click()
    await panelSettled(page)
    const aimed = await aimAtSubmit(page)
    expect(aimed.hitsButton).toBe(true)

    // Ten frames on. Nothing else moves the panel, so a height that has stopped changing is the
    // whole of the movement being over — which is what the helper concludes from it.
    await page.evaluate(() => new Promise<void>((resolve) => {
      let left = 10
      const step = () => (left-- > 0 ? requestAnimationFrame(step) : resolve())
      requestAnimationFrame(step)
    }))

    expect(await whatIsUnder(page, aimed)).toBe("the submit button")
  })
})
