import type {Locator} from "@playwright/test"
import {devices} from "@playwright/test"
import type {Page} from "./test"
import {expect, test} from "./test"
import {installApiMocks} from "./mocks"
import {pressSlice} from "./sliceBand"

/**
 * The two movements a member's slice is made of on a phone: the portrait's foot going soft, and
 * the description growing into the room below it.
 *
 * Runs only in the motion project, which is the one project that does not emulate reduced
 * motion — everywhere else both movements are over inside the reduced ceiling by design, so
 * this is the only place the choreography exists to be watched at all.
 *
 * That project runs a desktop device, and there is no stacked slice on a desktop. So this file
 * overrides its own context with a phone's rather than the config growing a fifth project: a
 * viewport and a touch screen are context options like any other, and the desktop motion specs
 * next to this one are untouched by a `use` in one file. Stated for the file rather than for a
 * describe inside it, because a device names the browser it expects and a worker cannot be
 * asked for a different browser part way through one.
 */
test.use({...devices["Pixel 7"]})

/**
 * A portrait as the api answers with one, at the widths one is stored at.
 *
 * Taller than it is wide, by half again: a stacked slice draws a portrait in a box of the aspect
 * the api reported, and the mocked file answers at that same shape. What the movements below are
 * measured against is the depth of the picture's band, so a fixture claiming a shape the bytes
 * are not would be measuring one against the other.
 */
const portrait = (name: string) => ({
  path: `board-portraits/${name}.webp`,
  url: `/files/public/board-portraits/${name}.webp`,
  width: 640,
  height: 960,
  renditions: [160, 320, 640].map((width) => ({url: `/files/public/board-portraits/${name}-${width}.webp`, width})),
})

/**
 * Two members, each with a portrait and something written about themselves.
 *
 * Both, because a slice that is open the moment the page draws itself has no opening left to
 * watch: the band opens the first thing that opens onto anything. So the one under the lens is
 * the second, the treasurer, which the seniority the page reads out of the roles puts below the
 * chair. And the words are a paragraph rather than a phrase, so the room they grow into is
 * unmistakably more than none.
 */
const CHAIR = 91
const WATCHED = 92

const twoMembers = [{
  id: 9, number: 9, name: "Eeveelutions", candidate: null, cheer: null, accent: null,
  description: null, startDate: "2025-09-01", endDate: null, image: null, photo: null, version: 0,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  members: [{
    id: CHAIR, boardId: 9, userId: null, role: "Chair", name: "Emma Dokter", nickname: "Emmz",
    description: "Chairing the ninth board.", image: null, portrait: portrait("emma"),
    startDate: "2025-09-01", endDate: null, version: 0,
    createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  }, {
    id: WATCHED, boardId: 9, userId: null, role: "Treasurer", name: "Viktor Petrov", nickname: null,
    description: "Keeping the books, chasing the invoices nobody wants to chase, and explaining "
      + "once a year where the association's money actually went. Ask me about the spreadsheet.",
    image: null, portrait: portrait("viktor"),
    startDate: "2025-09-01", endDate: null, version: 0,
    createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  }],
}]

/** One frame of what a reader can see of the slice, and how long after sampling began it is. */
interface Frame {
  at: number
  /** How deep into the picture the dissolve has gone, as a percentage of the portrait's band. */
  depth: number
  /** How tall the slice is, which is what the words growing under the picture makes taller. */
  height: number
}

/** How many frames are read after the slice reports itself open. 0.95s of ease, at 60 a second. */
const AFTER = 80

/**
 * The most frames that will ever be read, so a slice that never opens ends the sampling rather
 * than holding it open until the runner's own patience runs out.
 */
const AT_MOST = 300

/**
 * Every frame of the slice from now until [after] frames past the moment it reports itself open.
 *
 * Read off the page rather than sampled from here: a movement is only observable frame by frame,
 * and a poll from the test runner reads whichever frames the round trip happens to land on. The
 * depth is taken out of the mask the browser computed — where there is no mask there is no
 * dissolve, which is a depth of nothing on the same scale — and the height off the slice's own
 * box, which is what a reader watching the page sees get taller.
 *
 * The window ends at the slice rather than after a fixed count, because it has to begin before
 * the press and a press is two round trips from the runner. A fixed count of frames is a guess
 * at how long those take on a machine running eight of these at once, and the guess being wrong
 * means the window closes before the movement it exists to watch has begun. Anchored to what
 * the slice says about itself, how slow the press was does not come into it.
 */
async function frames(slice: Locator, after = AFTER): Promise<Frame[]> {
  return slice.evaluate((el, [wanted, cap]) => new Promise<Frame[]>((resolve) => {
    const started = performance.now()
    const taken: Frame[] = []
    let left = -1
    const tick = () => {
      const picture = el.querySelector("img")
      const stop = /(\d+(?:\.\d+)?)%/.exec(picture ? getComputedStyle(picture).maskImage : "")
      taken.push({
        at: performance.now() - started,
        depth: stop ? 100 - Number(stop[1]) : 0,
        height: el.getBoundingClientRect().height,
      })
      const open = el.querySelector("[aria-expanded]")?.getAttribute("aria-expanded") === "true"
      if (open && left < 0) left = wanted
      else if (left > 0) left -= 1
      if (left !== 0 && taken.length < cap) requestAnimationFrame(tick)
      else resolve(taken)
    }
    requestAnimationFrame(tick)
  }), [after, AT_MOST] as const)
}

/** The slice as it is in one frame, now. */
const frameNow = async (slice: Locator): Promise<Frame> => (await frames(slice, 0))[0]

/** How deep the dissolve rests, which is `--photo-dissolve`: 18% of the portrait's own band. */
const RESTING = 18

/**
 * Watches the slice across a press, rather than starting to watch once the press has returned.
 *
 * A press is a scroll and then a click, and both of those are round trips from the test runner.
 * Begun afterwards, the first frame read can already be the last one — and the whole of what
 * these tests are for is the middle of a movement, so a sampling window that may not contain it
 * is a test that fails on a busy machine and says nothing when it passes on a quiet one. This is
 * the same order the reduced pass below has always used, and for the same reason; it is stated
 * once here because both passes need it.
 *
 * The press cannot be awaited before sampling begins, so the promise is started, the press made
 * while it runs, and both awaited together.
 */
async function framesAcrossAPress(slice: Locator, after = AFTER): Promise<Frame[]> {
  const watching = frames(slice, after)
  await pressSlice(slice)
  return watching
}

/**
 * How far along their own time the opening is read, as shares of it.
 *
 * Anywhere strictly inside would do. These are early, because the ease is `cubic-bezier(0.22, 1,
 * 0.36, 1)`: three quarters of the movement is over in the first quarter of the time, so a
 * reading taken late is at the resting depth to within a tenth of a percent and says nothing.
 */
const ALONG = [0.05, 0.15, 0.35]

/** What the slice was holding at each of [ALONG], and which properties it was moving. */
interface Opening {
  properties: string[]
  held: Omit<Frame, "at">[]
}

/**
 * Watches the slice open, held at points along the transitions rather than at delivered frames.
 *
 * Reduced, the whole movement is over inside about an eighth of a second, so whether a sampler
 * ever sees the middle is a question about which frames `requestAnimationFrame` happened to
 * deliver on a busy machine — and the middle is the whole of what these cases are for. Caught at
 * `transitionrun`, which fires before the first frame of either movement is painted, and paused
 * there, the middle is wherever the transitions themselves put it and no machine can step over
 * it. Then released, so the slice settles where it would have settled anyway.
 *
 * The press cannot be awaited before watching begins, for the reason [framesAcrossAPress] gives.
 */
async function heldAcrossAPress(slice: Locator): Promise<Opening> {
  const watching = slice.evaluate((el, along) => new Promise<Opening>((resolve) => {
    el.addEventListener("transitionrun", () => {
      const running = el.getAnimations({subtree: true})
      const read = (share: number) => {
        for (const animation of running) {
          animation.pause()
          const timing = animation.effect!.getComputedTiming()
          animation.currentTime = Number(timing.delay ?? 0) + Number(timing.activeDuration ?? 0) * share
        }
        const picture = el.querySelector("img")
        const stop = /(\d+(?:\.\d+)?)%/.exec(picture ? getComputedStyle(picture).maskImage : "")
        return {depth: stop ? 100 - Number(stop[1]) : 0, height: el.getBoundingClientRect().height}
      }
      resolve({
        properties: running.map((animation) => (animation as CSSTransition).transitionProperty),
        held: along.map(read),
      })
      running.forEach((animation) => animation.play())
    }, {capture: true, once: true})
  }), ALONG)
  await pressSlice(slice)
  return watching
}

/** The page, with the chair's slice already open, and the treasurer's shut and waiting. */
async function boardOnAPhone(page: Page): Promise<Locator> {
  await installApiMocks(page, {boards: twoMembers})
  await page.goto("/board")

  const watched = page.getByTestId(`board-member-${WATCHED}`)
  await expect(page.getByTestId(`board-member-${CHAIR}`).getByRole("button"))
    .toHaveAttribute("aria-expanded", "true")
  await expect(watched.getByRole("button")).toHaveAttribute("aria-expanded", "false")
  return watched
}

test.describe("a member's slice opening on a phone", () => {
  test("eases the portrait's dissolve in rather than switching it on", async ({page}) => {
    const watched = await boardOnAPhone(page)

    const seen = await framesAcrossAPress(watched)

    // Shut, the picture is whole: what a dissolve on a shut slice would join it to is the next
    // person's face. The first frame is before the press, which is the point of watching across
    // it rather than after it.
    expect(seen[0].depth).toBe(0)

    // Partway, on frame after frame, rather than at its resting depth in the frame the slice
    // opened in. Depth only ever increases, because the dissolve is going one way.
    const partway = seen.filter((frame) => frame.depth > 0.1 && frame.depth < RESTING - 0.1)
    expect(partway.length).toBeGreaterThan(3)
    for (const [index, frame] of seen.entries()) {
      expect(frame.depth).toBeGreaterThanOrEqual((seen[index - 1]?.depth ?? 0) - 0.01)
    }

    // And it arrives where the island's own token puts it.
    await expect.poll(async () => (await frameNow(watched)).depth, {timeout: 5000})
      .toBeCloseTo(RESTING, 1)
  })

  test("grows the description into the room below rather than putting it there", async ({page}) => {
    const watched = await boardOnAPhone(page)

    const seen = await framesAcrossAPress(watched)

    // Shut, the slice is the portrait's band and the name on it, and nothing else. Read off the
    // first frame, which is before the press: the room below is what the press opens.
    const shut = seen[0].height
    const settled = seen[seen.length - 1].height

    // A paragraph's worth of room, arrived at through the heights in between rather than in one
    // step, and only ever downwards: the slice grows below what is being read.
    expect(settled).toBeGreaterThan(shut + 10)
    const partway = seen.filter((frame) => frame.height > shut + 1 && frame.height < settled - 1)
    expect(partway.length).toBeGreaterThan(3)
    for (const [index, frame] of seen.entries()) {
      expect(frame.height).toBeGreaterThanOrEqual((seen[index - 1]?.height ?? shut) - 0.01)
    }

    // Whatever the prose came to, uncapped: the words are inside the room the slice grew.
    const blurb = (await page.getByTestId(`board-member-blurb-${WATCHED}`).boundingBox())!
    const box = (await watched.boundingBox())!
    expect(blurb.y + blurb.height).toBeLessThanOrEqual(box.y + box.height + 1)
    await expect(page.getByTestId(`board-member-blurb-${WATCHED}`)).toBeVisible()
  })
})

test.describe("a member's slice opening for a visitor who asked for less motion", () => {
  test("clamps both movements to the ceiling the island allows", async ({page}) => {
    // Asked of the page rather than taken from the project: this file runs in the motion
    // project, which is the one project that asks for no preference at all.
    await page.emulateMedia({reducedMotion: "reduce"})
    const watched = await boardOnAPhone(page)

    // Watched across the press, which matters most here: reduced, the whole movement is over
    // inside an eighth of a second, and a test that cannot see the middle of a movement cannot
    // tell one that was shortened from one that was deleted, which is all this is for.
    const seen = await framesAcrossAPress(watched)

    const shut = seen[0]
    const settled = seen[seen.length - 1]

    // That both movements are still drawn rather than switched on is the sibling case below:
    // reduced, they are over inside an eighth of a second, which is too short a window to ask a
    // sampler to land in. What is claimed here is the ceiling itself.

    // Over almost at once, where the pass watched above is not a seventh of the way through:
    // the ceiling is what the preference buys, not the choreography.
    const began = seen.find((frame) => frame.depth > 0.1)!.at
    const moving = seen.filter((frame) => frame.at > began + 200
      && (Math.abs(frame.depth - settled.depth) > 0.1 || Math.abs(frame.height - settled.height) > 1))
    expect(moving, "frames still moving well after the ceiling").toEqual([])

    // And the end state is the same end state: the same dissolve, and the same room for the
    // same words. A preference asks for less movement, not for less of the page.
    expect(settled.depth).toBeCloseTo(RESTING, 1)
    expect(settled.height).toBeGreaterThan(shut.height + 10)
  })

  test("draws both movements rather than switching them on", async ({page}) => {
    await page.emulateMedia({reducedMotion: "reduce"})
    const watched = await boardOnAPhone(page)

    const shut = await frameNow(watched)
    const opening = await heldAcrossAPress(watched)

    // Reduced, not removed, which is the island's policy. Removed — `transition: none`, which is
    // what the stylesheet's own blankets do to everything they cover — the class change lands in
    // one frame and there is no transition here to hold at all.
    expect(opening.properties, "the movements the slice opens on")
      .toEqual(expect.arrayContaining(["--slice-dissolve", "grid-template-rows"]))

    // Released where the last reading left them, so where the slice comes to rest is its own doing.
    await expect.poll(async () => (await frameNow(watched)).depth, {timeout: 5000})
      .toBeCloseTo(RESTING, 1)
    const settled = await frameNow(watched)

    // Partway at every point read, and further on at each than at the one before: the dissolve is
    // going down the picture while the room below it is opening, rather than either arriving whole.
    for (const [index, held] of opening.held.entries()) {
      const before = opening.held[index - 1]
      expect(held.depth, `the dissolve ${ALONG[index] * 100}% of the way through`)
        .toBeGreaterThan(before?.depth ?? 0)
      expect(held.depth).toBeLessThan(RESTING - 0.1)
      expect(held.height, `the room below ${ALONG[index] * 100}% of the way through`)
        .toBeGreaterThan(before?.height ?? shut.height)
      expect(held.height).toBeLessThan(settled.height - 1)
    }
    expect(settled.height).toBeGreaterThan(shut.height + 10)
  })
})
