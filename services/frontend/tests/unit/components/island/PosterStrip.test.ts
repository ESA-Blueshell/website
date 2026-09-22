import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import PosterStrip from "@/components/island/PosterStrip.vue"

const poster = (id: number) => ({
  id,
  title: `Event ${id}`,
  meta: "February 2026",
  said: "What the art cannot say, in a line or two.",
  banner: `/art/${id}.webp`,
  srcset: `/art/${id}.webp 1600w`,
  width: 1600,
  height: 900,
})

const strip = (props: Record<string, unknown> = {}) =>
  mount(PosterStrip, {
    attachTo: document.body,
    props: {items: [1, 2, 3, 4, 5, 6].map(poster), testidPrefix: "events-strip", ...props},
  })

/* jsdom lays nothing out, so the scroller is told how wide it is and how far it has scrolled. */
const laidOut = (wrapper: ReturnType<typeof strip>, scrollLeft: number) => {
  const box = wrapper.get('[data-testid="events-strip"] > div').element as HTMLElement
  Object.defineProperty(box, "clientWidth", {configurable: true, value: 800})
  Object.defineProperty(box, "scrollWidth", {configurable: true, value: 2400})
  Object.defineProperty(box, "scrollLeft", {configurable: true, value: scrollLeft, writable: true})
  // jsdom scrolls nothing, so the two the strip asks for are answered here.
  box.scrollBy = vi.fn()
  box.scrollTo = vi.fn()
  return box
}

afterEach(() => {
  document.body.innerHTML = ""
  vi.restoreAllMocks()
})

describe("a strip of event posters", () => {
  it("draws every poster it is given, with its own art and what the art cannot say", () => {
    const wrapper = strip()

    expect(wrapper.findAll('[data-testid^="events-strip-"]').length).toBeGreaterThanOrEqual(6)
    const first = wrapper.get('[data-testid="events-strip-1"]')
    // The art carries the name and the date, so the foot carries what it cannot.
    expect(first.text()).not.toContain("Event 1")
    expect(first.text()).toContain("February 2026")
    expect(first.text()).toContain("What the art cannot say")
    expect(first.get(".posters__said").html()).toContain("<p>")
    expect(first.get("img").attributes("srcset")).toBe("/art/1.webp 1600w")
    expect(first.attributes("type")).toBe("button")
  })

  it("says which poster was pressed", async () => {
    const wrapper = strip()

    await wrapper.get('[data-testid="events-strip-3"]').trigger("click")

    expect(wrapper.emitted("open")).toEqual([[3]])
  })

  it("leads out of the page where the caller gave an address, and says nothing where it did not", () => {
    const linked = mount(PosterStrip, {
      props: {
        items: [{id: 9, title: "Gone", meta: "2024", banner: "/art/9.webp", href: "/events"}],
        testidPrefix: "p",
      },
    })
    const bare = mount(PosterStrip, {
      props: {items: [{id: 9, title: "Gone", banner: "/art/9.webp"}], testidPrefix: "b"},
    })

    expect(linked.get("a").attributes("href")).toBe("/events")
    expect(bare.find("a").exists()).toBe(false)
    expect(bare.get('[data-testid="b-9"]').attributes("type")).toBe("button")
    expect(bare.find(".posters__meta").exists()).toBe(false)
    // The title stands in where the caller has nothing to say about the event.
    expect(bare.get(".posters__said").text()).toBe("Gone")
  })

  it("draws the association's own template where nobody made a poster", () => {
    const wrapper = mount(PosterStrip, {
      props: {
        items: [
          {
            id: 4,
            title: "Scouting grounds League of Legends",
            when: "20 September - 12:00-17:00",
            where: "Esports Lounge Twente",
            said: "Come and be seen.",
          },
          {id: 5, title: "A quiet one"},
        ],
        testidPrefix: "p",
      },
    })

    const drawn = wrapper.get('[data-testid="p-4"] .posters__plate')
    expect(drawn.get(".posters__plate-title").text()).toBe("Scouting grounds League of Legends")
    expect(drawn.text()).toContain("20 September - 12:00-17:00")
    expect(drawn.text()).toContain("Esports Lounge Twente")

    // A day and a place the caller does not know are left off rather than written blank.
    const bare = wrapper.get('[data-testid="p-5"] .posters__plate')
    expect(bare.findAll(".posters__plate-line")).toHaveLength(0)
  })

  it("lights the poster under the pointer and quietens the rest, and lets go on leaving", async () => {
    const wrapper = strip()
    const one = wrapper.get('[data-testid="events-strip-2"]')

    await one.trigger("mouseenter")
    expect(one.classes()).toContain("posters__poster--lit")
    expect(wrapper.get('[data-testid="events-strip"]').classes()).toContain("posters--quiet")

    await wrapper.get('[data-testid="events-strip"]').trigger("mouseleave")
    expect(wrapper.get('[data-testid="events-strip-2"]').classes())
      .not.toContain("posters__poster--lit")
  })

  it("lights a poster somebody reached by keyboard", async () => {
    const wrapper = strip()

    await wrapper.get('[data-testid="events-strip-3"]').trigger("focusin")

    expect(wrapper.get('[data-testid="events-strip-3"]').classes())
      .toContain("posters__poster--lit")
  })

  it("offers the way on only while there is more that way", async () => {
    const wrapper = strip()
    const box = laidOut(wrapper, 0)

    box.dispatchEvent(new Event("scroll"))
    await flushPromises()
    expect(wrapper.find('[data-testid="events-strip-pan-on"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="events-strip-pan-back"]').exists()).toBe(false)

    Object.defineProperty(box, "scrollLeft", {configurable: true, value: 600, writable: true})
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()
    expect(wrapper.find('[data-testid="events-strip-pan-back"]').exists()).toBe(true)

    Object.defineProperty(box, "scrollLeft", {configurable: true, value: 1600, writable: true})
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()
    expect(wrapper.find('[data-testid="events-strip-pan-on"]').exists()).toBe(false)
  })

  it("flips two posters when a chevron is pressed", async () => {
    const wrapper = strip()
    const box = laidOut(wrapper, 600)
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    await wrapper.get('[data-testid="events-strip-pan-on"]').trigger("click")
    await wrapper.get('[data-testid="events-strip-pan-back"]').trigger("click")

    const asked = (box.scrollBy as ReturnType<typeof vi.fn>).mock.calls
      .map(([one]) => (one as {left: number}).left)
    // A poster is a quarter of the 800 the strip is wide, so two of them is 400.
    expect(asked[0]).toBe(400)
    expect(asked[1]).toBe(-400)
  })

  it("flips without easing where somebody asked for less motion", async () => {
    vi.stubGlobal("matchMedia", (query: string) => ({
      matches: query.includes("reduce"), media: query, onchange: null,
      addListener: () => {}, removeListener: () => {},
      addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false,
    }))
    const wrapper = strip()
    const box = laidOut(wrapper, 600)
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    await wrapper.get('[data-testid="events-strip-pan-on"]').trigger("click")

    const [asked] = (box.scrollBy as ReturnType<typeof vi.fn>).mock.calls[0] as [{behavior: string}]
    expect(asked.behavior).toBe("auto")
    vi.unstubAllGlobals()
  })

  it("measures itself again when the strip is handed more posters", async () => {
    const queue: Array<(at: number) => void> = []
    vi.stubGlobal("requestAnimationFrame", (run: (at: number) => void) => queue.push(run))
    const wrapper = strip({items: [poster(1)]})
    laidOut(wrapper, 0)

    await wrapper.setProps({items: [1, 2, 3, 4, 5, 6].map(poster)})
    for (const run of queue.splice(0)) run(0)
    await flushPromises()

    expect(wrapper.find('[data-testid="events-strip-pan-on"]').exists()).toBe(true)
    vi.unstubAllGlobals()
  })

  it("says it needs more before the last poster is reached", async () => {
    const wrapper = strip()
    const box = laidOut(wrapper, 1400)

    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    expect(wrapper.emitted("needs-more")).toBeTruthy()
  })

  it("asks for nothing while the strip holds what fits", async () => {
    const wrapper = strip({items: [poster(1)]})
    const box = wrapper.get('[data-testid="events-strip"] > div').element as HTMLElement
    Object.defineProperty(box, "clientWidth", {configurable: true, value: 800})
    Object.defineProperty(box, "scrollWidth", {configurable: true, value: 800})

    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    expect(wrapper.emitted("needs-more")).toBeUndefined()
  })
})
