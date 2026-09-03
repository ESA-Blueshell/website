import type {Locator} from "@playwright/test"
import {devices} from "@playwright/test"
import type {Page} from "./test"
import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

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

/** A portrait as the api answers with one, at the widths one is stored at. */
const portrait = (name: string) => ({
  path: `board-portraits/${name}.webp`,
  url: `/files/public/board-portraits/${name}.webp`,
  width: 640,
  height: 640,
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

/**
 * Every frame of the slice for as long as it takes to draw [count] of them.
 *
 * Read off the page rather than sampled from here: a movement is only observable frame by
 * frame, and a poll from the test runner reads whichever frames the round trip happens to land
 * on. The depth is taken out of the mask the browser computed — where there is no mask there is
 * no dissolve, which is a depth of nothing on the same scale — and the height off the slice's
 * own box, which is what a reader watching the page sees get taller.
 */
async function frames(slice: Locator, count: number): Promise<Frame[]> {
  return slice.evaluate((el, wanted) => new Promise<Frame[]>((resolve) => {
    const started = performance.now()
    const taken: Frame[] = []
    const tick = () => {
      const picture = el.querySelector("img")
      const stop = /(\d+(?:\.\d+)?)%/.exec(picture ? getComputedStyle(picture).maskImage : "")
      taken.push({
        at: performance.now() - started,
        depth: stop ? 100 - Number(stop[1]) : 0,
        height: el.getBoundingClientRect().height,
      })
      if (taken.length < wanted) requestAnimationFrame(tick)
      else resolve(taken)
    }
    requestAnimationFrame(tick)
  }), count)
}

/** How deep the dissolve rests, which is `--photo-dissolve`: 18% of the portrait's own band. */
const RESTING = 18

/**
 * Presses a member, having first put it where pressing it will not scroll the page.
 *
 * The same order the board spec presses in, and for the same reason: stacked, a scroll decides
 * which slice is open and releases a tap by design, and the scroll Playwright does as part of a
 * click arrives after the click it belongs to.
 */
async function press(slice: Locator): Promise<void> {
  await slice.scrollIntoViewIfNeeded()
  await expect(slice).toBeInViewport()
  await slice.getByRole("button").click()
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

    // Shut, the picture is whole: what a dissolve on a shut slice would join it to is the next
    // person's face.
    expect((await frames(watched, 1))[0].depth).toBe(0)

    await press(watched)
    const seen = await frames(watched, 70)

    // Partway, on frame after frame, rather than at its resting depth in the frame the slice
    // opened in. Depth only ever increases, because the dissolve is going one way.
    const partway = seen.filter((frame) => frame.depth > 0.1 && frame.depth < RESTING - 0.1)
    expect(partway.length).toBeGreaterThan(3)
    for (const [index, frame] of seen.entries()) {
      expect(frame.depth).toBeGreaterThanOrEqual((seen[index - 1]?.depth ?? 0) - 0.01)
    }

    // And it arrives where the island's own token puts it.
    await expect.poll(async () => (await frames(watched, 1))[0].depth, {timeout: 5000})
      .toBeCloseTo(RESTING, 1)
  })

  test("grows the description into the room below rather than putting it there", async ({page}) => {
    const watched = await boardOnAPhone(page)

    // Shut, the slice is the portrait's band and the name on it, and nothing else.
    const shut = (await frames(watched, 1))[0].height

    await press(watched)
    const seen = await frames(watched, 70)
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
    /*
     * Asked of the page rather than declared as an option, which is what the season spec next
     * door does and for the same reason: on Playwright 1.60 `use.reducedMotion` does not reach
     * the page at all, so `matchMedia` answers false however the option is set — #852. A test of
     * what a visitor with the preference gets has to actually be one, so it says so here. Once
     * #852 is fixed this line is what should go.
     */
    await page.emulateMedia({reducedMotion: "reduce"})
    const watched = await boardOnAPhone(page)
    const shut = (await frames(watched, 1))[0].height

    await press(watched)
    const seen = await frames(watched, 40)
    const settled = seen[seen.length - 1]

    // Reduced rather than removed, which is the island's policy: both movements still happen,
    // they are simply over. A quarter of a second in, nothing is still moving — where the pass
    // watched above is only half way through it — so anything sampled after that is at rest.
    const after = seen.filter((frame) => frame.at > 250)
    expect(after.length).toBeGreaterThan(3)
    for (const frame of after) {
      expect(frame.depth).toBeCloseTo(settled.depth, 1)
      expect(frame.height).toBeCloseTo(settled.height, 0)
    }

    // And the end state is the same end state: the same dissolve, and the same room for the
    // same words. A preference asks for less movement, not for less of the page.
    expect(settled.depth).toBeCloseTo(RESTING, 1)
    expect(settled.height).toBeGreaterThan(shut + 10)
  })
})
