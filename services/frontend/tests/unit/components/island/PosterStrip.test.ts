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

  it("moves a screenful when a chevron is pressed", async () => {
    const wrapper = strip()
    const box = laidOut(wrapper, 600)
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    await wrapper.get('[data-testid="events-strip-pan-on"]').trigger("click")
    await wrapper.get('[data-testid="events-strip-pan-back"]').trigger("click")

    const asked = (box.scrollBy as ReturnType<typeof vi.fn>).mock.calls
      .map(([one]) => (one as {left: number}).left)
    expect(asked[0]).toBeGreaterThan(0)
    expect(asked[1]).toBeLessThan(0)
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

describe("the strip travelling under a resting pointer", () => {
  /* jsdom runs no frames of its own, so each one is asked for by hand. */
  const frames = () => {
    const queue: Array<(at: number) => void> = []
    // The loop measures against the clock it started on, so that clock starts at zero too.
    vi.spyOn(performance, "now").mockReturnValue(0)
    vi.stubGlobal("requestAnimationFrame", (run: (at: number) => void) => {
      queue.push(run)
      return queue.length
    })
    vi.stubGlobal("cancelAnimationFrame", vi.fn())
    return {
      run: (at: number) => {
        const next = queue.shift()
        if (next) next(at)
      },
      queue,
    }
  }

  /* A pointer that rests, and motion nobody asked to have less of. */
  const hovering = (yes: boolean) => {
    vi.stubGlobal("matchMedia", (query: string) => ({
      matches: query.includes("hover") ? yes : false, media: query, onchange: null,
      addListener: () => {}, removeListener: () => {},
      addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false,
    }))
  }

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("travels while the pointer rests on a side, and stops between the sides", async () => {
    hovering(true)
    const clock = frames()
    const wrapper = strip()
    const box = laidOut(wrapper, 600)
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    const row = wrapper.get('[data-testid="events-strip"]')
    row.element.getBoundingClientRect = () => ({left: 0, width: 800} as DOMRect)

    await row.trigger("mousemove", {clientX: 10})
    clock.run(0)
    clock.run(100)
    expect(box.scrollLeft).toBeLessThan(600)
    expect(wrapper.get('[data-testid="events-strip-pan-back"]').classes())
      .toContain("posters__pan--live")

    await row.trigger("mousemove", {clientX: 790})
    clock.run(200)
    clock.run(300)
    expect(wrapper.get('[data-testid="events-strip-pan-on"]').classes())
      .toContain("posters__pan--live")

    await row.trigger("mousemove", {clientX: 400})
    expect(wrapper.get('[data-testid="events-strip-pan-on"]').classes())
      .not.toContain("posters__pan--live")
  })

  it("stops travelling where there is no further to go, and when it is taken off the page", async () => {
    hovering(true)
    const clock = frames()
    const wrapper = strip()
    const box = laidOut(wrapper, 1400)
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    const row = wrapper.get('[data-testid="events-strip"]')
    row.element.getBoundingClientRect = () => ({left: 0, width: 800} as DOMRect)

    await row.trigger("mousemove", {clientX: 790})
    clock.run(0)
    clock.run(1000)
    expect(clock.queue.length).toBe(0)

    await row.trigger("mousemove", {clientX: 10})
    wrapper.unmount()
    expect(cancelAnimationFrame).toHaveBeenCalled()
  })

  /* A finger is either on the strip or off it, so a tap near an edge must not set it moving. */
  it("leaves a touch screen alone", async () => {
    hovering(false)
    frames()
    const wrapper = strip()
    const box = laidOut(wrapper, 600)
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    const row = wrapper.get('[data-testid="events-strip"]')
    row.element.getBoundingClientRect = () => ({left: 0, width: 800} as DOMRect)
    await row.trigger("mousemove", {clientX: 10})

    expect(wrapper.get('[data-testid="events-strip-pan-back"]').classes())
      .not.toContain("posters__pan--live")
  })

  it("measures itself again when the strip is handed more posters", async () => {
    hovering(true)
    const clock = frames()
    const wrapper = strip({items: [poster(1)]})
    laidOut(wrapper, 0)

    await wrapper.setProps({items: [1, 2, 3, 4, 5, 6].map(poster)})
    clock.run(0)
    await flushPromises()

    expect(wrapper.find('[data-testid="events-strip-pan-on"]').exists()).toBe(true)
  })

  it("stops the frame it had already asked for once the pointer leaves the side", async () => {
    hovering(true)
    const clock = frames()
    const wrapper = strip()
    const box = laidOut(wrapper, 600)
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    const row = wrapper.get('[data-testid="events-strip"]')
    row.element.getBoundingClientRect = () => ({left: 0, width: 800} as DOMRect)
    await row.trigger("mousemove", {clientX: 10})
    clock.run(0)

    await row.trigger("mousemove", {clientX: 400})
    const before = box.scrollLeft
    clock.run(500)

    expect(box.scrollLeft).toBe(before)
  })

  it("moves without easing where somebody asked for less motion", async () => {
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
  })

  it("travels nothing before it has been laid out", async () => {
    hovering(true)
    frames()
    const wrapper = mount(PosterStrip, {props: {items: [poster(1)], testidPrefix: "bare"}})

    await wrapper.get('[data-testid="bare"]').trigger("mousemove", {clientX: 10})

    expect(wrapper.find('[data-testid="bare-pan-back"]').exists()).toBe(false)
  })

  it("comes to rest on the next poster the way it was going", async () => {
    vi.useFakeTimers()
    hovering(true)
    const clock = frames()
    const wrapper = strip()
    const box = laidOut(wrapper, 400)
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    const row = wrapper.get('[data-testid="events-strip"]')
    row.element.getBoundingClientRect = () => ({left: 0, width: 800} as DOMRect)
    await row.trigger("mousemove", {clientX: 790})
    clock.run(0)
    clock.run(100)

    await row.trigger("mouseleave")

    const [asked] = (box.scrollTo as ReturnType<typeof vi.fn>).mock.calls[0] as [{left: number}]
    // A poster is a quarter of the 800 the strip is wide, so 400 lands on the third of them.
    expect(asked.left).toBe(600)
    expect(wrapper.get(".posters__scroll").classes()).toContain("posters__scroll--travelling")

    vi.advanceTimersByTime(600)
    await flushPromises()
    expect(wrapper.get(".posters__scroll").classes()).not.toContain("posters__scroll--travelling")
    vi.useRealTimers()
  })

  it("rests where it stands when the strip was not travelling", async () => {
    hovering(true)
    frames()
    const wrapper = strip()
    const box = laidOut(wrapper, 400)
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    await wrapper.get('[data-testid="events-strip"]').trigger("mouseleave")

    expect(box.scrollTo).not.toHaveBeenCalled()
  })

  it("comes to rest on the poster behind it when it was travelling back", async () => {
    hovering(true)
    const clock = frames()
    const wrapper = strip()
    const box = laidOut(wrapper, 500)
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    const row = wrapper.get('[data-testid="events-strip"]')
    row.element.getBoundingClientRect = () => ({left: 0, width: 800} as DOMRect)
    await row.trigger("mousemove", {clientX: 10})
    clock.run(0)
    await row.trigger("mouseleave")

    const [asked] = (box.scrollTo as ReturnType<typeof vi.fn>).mock.calls[0] as [{left: number}]
    expect(asked.left).toBe(400)
  })

  it("comes to rest without easing where somebody asked for less motion", async () => {
    // A pointer that rests, and somebody who asked for less motion: both answer here.
    vi.stubGlobal("matchMedia", (query: string) => ({
      matches: query.includes("reduce") || query.includes("hover"), media: query, onchange: null,
      addListener: () => {}, removeListener: () => {},
      addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false,
    }))
    const clock = frames()
    const wrapper = strip()
    const box = laidOut(wrapper, 400)
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    const row = wrapper.get('[data-testid="events-strip"]')
    row.element.getBoundingClientRect = () => ({left: 0, width: 800} as DOMRect)
    // Twice over, so the wait it had already started is dropped rather than doubled.
    await row.trigger("mousemove", {clientX: 790})
    clock.run(0)
    await row.trigger("mouseleave")
    await row.trigger("mousemove", {clientX: 790})
    clock.run(100)
    await row.trigger("mouseleave")

    const calls = (box.scrollTo as ReturnType<typeof vi.fn>).mock.calls as Array<[{behavior: string}]>
    expect(calls[0]![0].behavior).toBe("auto")
    expect(calls).toHaveLength(2)
  })

  it("rests without a pitch to rest on, where the strip has no width yet", async () => {
    hovering(true)
    const clock = frames()
    const wrapper = strip()
    const box = laidOut(wrapper, 400)
    Object.defineProperty(box, "clientWidth", {configurable: true, value: 0})
    box.dispatchEvent(new Event("scroll"))
    await flushPromises()

    const row = wrapper.get('[data-testid="events-strip"]')
    row.element.getBoundingClientRect = () => ({left: 0, width: 800} as DOMRect)
    await row.trigger("mousemove", {clientX: 790})
    clock.run(0)
    await row.trigger("mouseleave")

    expect(box.scrollTo).not.toHaveBeenCalled()
  })
})
