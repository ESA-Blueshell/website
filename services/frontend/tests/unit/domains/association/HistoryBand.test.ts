import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import HistoryBand from "@/domains/association/island/HistoryBand.vue"
import type {Milestone} from "@/domains/association/historyAxis"

const MILESTONES: Milestone[] = [
  {year: "2017", title: "First", summary: "The first, in a line.", telling: "The first thing that happened."},
  {year: "2019", title: "Second", summary: "The second, in a line.", telling: "The second thing that happened."},
  {year: "Now", title: "Third", summary: "Where it stands, in a line.", telling: "Where it stands today."},
]

const SCREEN = 900
const STOP = 200

const mountBand = (): VueWrapper => mount(HistoryBand, {props: {milestones: MILESTONES, testid: "history"}})

const place = (element: Element, top: number, height: number): void => {
  element.getBoundingClientRect = () => ({
    top, bottom: top + height, height, left: 0, right: 0, width: 0, x: 0, y: top, toJSON: () => ({}),
  }) as DOMRect
}

/**
 * Put the list on the screen with the given stop's middle at the middle of the screen.
 *
 * jsdom lays nothing out, so the stops are placed by hand: three of them in a column, moved as
 * one so that scrolling is the whole column sliding up past a fixed middle.
 */
const scrollTo = async (wrapper: VueWrapper, top: number): Promise<void> => {
  place(wrapper.get(".history__line").element, top, STOP * MILESTONES.length)
  wrapper.findAll('[data-testid="history-stop"]').forEach((stop, index) => {
    place(stop.element, top + index * STOP, STOP)
  })
  window.dispatchEvent(new Event("scroll"))
  await new Promise(resolve => requestAnimationFrame(() => resolve(null)))
  await wrapper.vm.$nextTick()
}

/** Where the column has to start for the given stop's middle to sit at the middle of the screen. */
const middleOf = (index: number): number => SCREEN / 2 - index * STOP - STOP / 2

/** Waits out the writing: the longest line here at the pace it is written at, and then some. */
const written = async (wrapper: VueWrapper): Promise<void> => {
  await new Promise(resolve => setTimeout(resolve, 2600))
  await wrapper.vm.$nextTick()
}

/**
 * What a reader can actually read of a milestone's telling.
 *
 * Not `text()`: the part not yet written is on the page the whole time, in no colour, holding
 * the room the words will need. Reading the element's text would count that as written.
 */
type Part = {exists: () => boolean; text: () => string}

const shown = (stop: {find: (selector: string) => Part}): string => {
  const text = (selector: string): string => (stop.find(selector).exists() ? stop.find(selector).text() : "")
  // The character just written has a box of its own, so that it can land in it.
  return `${text(".history__written")}${text(".history__landing")}`
}

const readStop = (wrapper: VueWrapper): number =>
  wrapper.findAll('[data-testid="history-stop"]').findIndex(stop => stop.classes().includes("history__stop--read"))

describe("HistoryBand", () => {
  beforeEach(() => {
    vi.unstubAllGlobals()
    window.innerHeight = SCREEN
    vi.stubGlobal("matchMedia", (query: string) => ({
      matches: false,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))
  })

  it("draws every milestone, in the order they happened", () => {
    const wrapper = mountBand()

    const stops = wrapper.findAll('[data-testid="history-stop"]')
    expect(stops).toHaveLength(3)
    expect(stops[0].text()).toContain("First")
    expect(stops[2].text()).toContain("Third")
  })

  /**
   * A milestone shows none of its line until it is reached.
   *
   * Drawing the summary beforehand would mean taking it off the page again for the writing to
   * start, and text that vanishes to come back is worse than text that was never there.
   */
  it("writes nothing for a milestone nobody has reached", () => {
    const wrapper = mountBand()

    for (const stop of wrapper.findAll('[data-testid="history-stop"]')) {
      expect(shown(stop)).toBe("")
    }
    expect(wrapper.findAll(".history__stop--read")).toHaveLength(0)
  })

  /** The one at the middle of the screen is the one being read, and only that one. */
  it("reads whichever milestone reaches the middle", async () => {
    const wrapper = mountBand()

    await scrollTo(wrapper, middleOf(1))

    expect(readStop(wrapper)).toBe(1)
    expect(wrapper.findAll(".history__stop--read")).toHaveLength(1)
  })

  /**
   * The reason this is measured rather than watched.
   *
   * A wheel flick moves the page further in one frame than a milestone is tall, so the middle
   * of the screen can be past a stop before anything is asked about it. The stop nearest the
   * middle still answers, so every milestone opens as it goes by.
   */
  it("reads a milestone the page flew past in a single frame", async () => {
    const wrapper = mountBand()

    await scrollTo(wrapper, middleOf(0))
    expect(readStop(wrapper)).toBe(0)

    // A single frame carrying the reader most of the way through the second milestone.
    await scrollTo(wrapper, middleOf(1) - STOP * 0.4)
    expect(readStop(wrapper)).toBe(1)
  })

  /** No dead ground between two milestones: one of them is always the one being read. */
  it("reads a milestone while the middle sits between two of them", async () => {
    const wrapper = mountBand()

    await scrollTo(wrapper, middleOf(1) - STOP / 2)

    expect(wrapper.findAll(".history__stop--read")).toHaveLength(1)
  })

  /** The telling carries on from the summary, and only for the milestone being read. */
  it("writes the telling out once a milestone reaches the middle", async () => {
    const wrapper = mountBand()
    expect(shown(wrapper.findAll('[data-testid="history-stop"]')[1])).not.toContain(MILESTONES[1].telling)

    await scrollTo(wrapper, middleOf(1))
    await written(wrapper)

    const stops = wrapper.findAll('[data-testid="history-stop"]')
    expect(shown(stops[1])).toContain(MILESTONES[1].telling)
    expect(shown(stops[0])).not.toContain(MILESTONES[0].telling)
  })

  /**
   * Every milestone holds the room its telling needs from the start.
   *
   * Otherwise arriving at one grows it, everything below it moves down the page, and the page
   * shifts out from under the reader who was scrolling towards it.
   */
  it("keeps the room for every telling whether or not it has been written", () => {
    const wrapper = mountBand()

    const waiting = wrapper.findAll(".history__waiting").map(part => part.text())
    expect(waiting).toEqual(MILESTONES.map(milestone => `${milestone.summary} ${milestone.telling}`))
  })

  /** The console's blinking block, waiting at the end of the writing and gone once it stops. */
  it("keeps a cursor at the end of the writing until the telling is finished", async () => {
    const wrapper = mountBand()

    await scrollTo(wrapper, middleOf(1))
    expect(wrapper.findAll(".history__cursor")).toHaveLength(1)

    await written(wrapper)

    expect(wrapper.findAll(".history__cursor")).toHaveLength(0)
  })

  /**
   * A milestone a reader has watched write itself out is theirs to go back to.
   *
   * Taking it off the page as they scroll on leaves them nothing to return to, and no way to
   * get it back but to find the exact spot again.
   */
  it("keeps a milestone written once the reader has been past it", async () => {
    const wrapper = mountBand()

    await scrollTo(wrapper, middleOf(1))
    await written(wrapper)
    await scrollTo(wrapper, middleOf(2))

    const stops = wrapper.findAll('[data-testid="history-stop"]')
    expect(shown(stops[1])).toContain(MILESTONES[1].telling)
    // The one before it was never reached, so it still has nothing to show.
    expect(shown(stops[0])).toBe("")
  })

  /** Left half-written, the rest arrives at once rather than standing unfinished. */
  it("finishes a milestone the reader left part-way through", async () => {
    const wrapper = mountBand()

    await scrollTo(wrapper, middleOf(1))
    await scrollTo(wrapper, middleOf(2))

    const stops = wrapper.findAll('[data-testid="history-stop"]')
    expect(shown(stops[1])).toContain(MILESTONES[1].telling)
  })

  it("stops reading once the whole history is behind the reader", async () => {
    const wrapper = mountBand()
    await scrollTo(wrapper, middleOf(1))

    await scrollTo(wrapper, -STOP * MILESTONES.length - 10)

    expect(wrapper.findAll(".history__stop--read")).toHaveLength(0)
    expect(wrapper.findAll(".history__cursor")).toHaveLength(0)
  })

  /**
   * A reader who asked for less motion gets the whole history at once.
   *
   * Nothing grows and nothing opens, so nothing is hidden behind an effect they turned off.
   */
  it("opens every milestone for a reader who asked for less motion", async () => {
    vi.stubGlobal("matchMedia", (query: string) => ({
      matches: true,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))

    const wrapper = mountBand()

    expect(wrapper.classes()).toContain("history--still")
    for (const stop of wrapper.findAll('[data-testid="history-stop"]')) {
      expect(stop.classes()).toContain("history__stop--read")
    }
    // Nothing to measure for: there is no growing to trigger.
    await scrollTo(wrapper, -STOP * MILESTONES.length - 10)
    for (const stop of wrapper.findAll('[data-testid="history-stop"]')) {
      expect(stop.classes()).toContain("history__stop--read")
    }
  })
})
