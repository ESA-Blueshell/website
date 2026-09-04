import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, h, nextTick, ref, type Ref} from "vue"
import SliceBand from "@/components/island/SliceBand.vue"
import {provideTravelling} from "@/components/island/bandTravel.ts"

const items = [
  {id: 1, title: "BS Waterboarders", meta: "5 on the roster", banner: "/a.jpg"},
  {id: 2, title: "BS SpicyWater", meta: "2 on the roster", banner: ""},
]

const mountSlices = () =>
  mount(SliceBand, {
    props: {items, accent: "#ff4655", testidPrefix: "team-roster"},
    slots: {details: '<span class="detail">{{ params.item.title }} roster</span>'},
  })

const settled = () => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))

/**
 * A band a pass carried in with nothing in it yet, which is how a stop that has to be fetched
 * arrives: the season under the finger is only read once the gesture claims the axis.
 *
 * Built inside something that says a pass is on, rather than told so afterwards, because the
 * decision is taken as the band is built — see the arrival in the component.
 */
const carriedIn = () => {
  const held = ref<typeof items>([])
  let travelling!: Ref<boolean>
  const wrapper = mount(defineComponent({
    setup() {
      travelling = provideTravelling()
      travelling.value = true
      return () => h(SliceBand, {items: held.value, accent: "#ff4655", testidPrefix: "team-roster"})
    },
  }))
  return {wrapper, held, travelling}
}

const opened = (wrapper: ReturnType<typeof mountSlices>, index: number) =>
  wrapper.findAll("section")[index]?.classes() ?? []

describe("SliceBand", () => {
  it("names each slice by the prefix its page uses", () => {
    const wrapper = mountSlices()

    expect(wrapper.find('[data-testid="team-roster-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="team-roster-2"]').exists()).toBe(true)
  })

  it("carries a banner where there is one, and none where there is not", () => {
    const wrapper = mountSlices()
    const slices = wrapper.findAll("section")

    expect(slices[0].find("img").attributes("src")).toBe("/a.jpg")
    expect(slices[1].find("img").exists()).toBe(false)
  })

  it("shows the title and its line of meta while shut", () => {
    const wrapper = mountSlices()

    expect(wrapper.text()).toContain("BS Waterboarders")
    expect(wrapper.text()).toContain("5 on the roster")
  })

  it("opens the first slice, and reports which is open", async () => {
    const wrapper = mountSlices()
    await settled()

    const slices = wrapper.findAll("section")
    expect(slices[0].classes()).toContain("slice--open")
    expect(slices[1].classes()).not.toContain("slice--open")
    expect(slices[0].get("button").attributes("aria-expanded")).toBe("true")
  })

  it("opens the slice under the pointer instead", async () => {
    const wrapper = mountSlices()
    await settled()
    const slices = wrapper.findAll("section")

    await slices[1].trigger("mouseenter")

    expect(slices[1].classes()).toContain("slice--open")
    expect(slices[0].classes()).not.toContain("slice--open")
  })

  it("opens a slice on a click, since a touch screen has no hover to give", async () => {
    const wrapper = mountSlices()
    await settled()
    const slices = wrapper.findAll("section")

    await slices[1].get("button").trigger("click")

    expect(slices[1].get("button").attributes("aria-expanded")).toBe("true")
  })

  it("hands each slice's own item to whatever the page renders inside it", () => {
    const wrapper = mountSlices()

    const details = wrapper.findAll(".detail").map(d => d.text())
    expect(details).toEqual(["BS Waterboarders roster", "BS SpicyWater roster"])
  })

  /**
   * How large a copy of a banner the browser is asked for.
   *
   * Two passes. The first understates so that something is on the screen quickly; the second
   * is worked out from the two things that decide the width (how many slices share the row and
   * how wide the window is) and waits for the first copy to arrive rather than racing it.
   *
   * jsdom's window is 1024 wide, and the fixture is two slices with the first one open: so the
   * open slice takes 3.4 of 4.4 shares, which is 792, and the shut one takes 1 of 4.4, 233.
   */
  it("asks for a small copy until one has arrived", async () => {
    const wrapper = mountSlices()
    await settled()

    // Laid out, opened, and still on the understated promise: nothing has loaded.
    expect(wrapper.find("img").attributes("sizes")).toBe("(min-width: 768px) 200px, 50vw")
  })

  it("asks for the width its share of the row works out to, once a copy has arrived", async () => {
    const wrapper = mountSlices()
    await settled()

    await wrapper.find("img").trigger("load")

    // The first slice is the open one, so 3.4 shares of 4.4 across a 1024 window.
    expect(wrapper.find("img").attributes("sizes")).toBe("792px")
  })

  it("asks for more as a slice opens, and never for less once it has shut again", async () => {
    // Both slices carry a picture here, because the point is which of the two is asked for
    // more: the shared fixture gives the second one none.
    const wrapper = mount(SliceBand, {
      props: {
        items: [
          {id: 1, title: "One", meta: "", banner: "/a.jpg"},
          {id: 2, title: "Two", meta: "", banner: "/b.jpg"},
        ],
        accent: "#ff4655",
        testidPrefix: "team-roster",
      },
    })
    await settled()
    const slices = wrapper.findAll("section")
    const asked = (index: number) => slices[index].find("img").attributes("sizes")

    await slices[1].find("img").trigger("load")
    // Shut, so one share of 4.4.
    expect(asked(1)).toBe("233px")

    await slices[1].trigger("mouseenter")
    expect(asked(1)).toBe("792px")

    // Shut again, and still asking for the copy it already has: a browser will not swap a
    // picture for a smaller one, so asking for less would achieve nothing.
    await slices[0].trigger("mouseenter")
    expect(asked(1)).toBe("792px")
  })

  /** Stacked, a slice is the width of the window and the row's shares do not come into it. */
  it("asks for the width of the window where the slices are stacked", async () => {
    const wide = window.matchMedia
    window.matchMedia = ((query: string) => ({
      matches: query.includes("max-width: 767px"),
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    })) as unknown as typeof window.matchMedia
    try {
      const wrapper = mountSlices()
      await settled()

      await wrapper.find("img").trigger("load")

      expect(wrapper.find("img").attributes("sizes")).toBe("1024px")
    } finally {
      window.matchMedia = wide
    }
  })

  it("opens the slices a pass was carrying, however late they arrive", async () => {
    const {wrapper, held} = carriedIn()

    held.value = items
    await nextTick()

    // Open in the update they arrived in rather than two frames after it, which is a slice growing
    // once the page has stopped moving: the gesture was the animation.
    expect(opened(wrapper, 0)).toContain("slice--open")
  })

  it("opens them where they land after the pass has already ended", async () => {
    const {wrapper, held, travelling} = carriedIn()

    // The pass over with nothing yet to draw, which is the case the memory of having been carried
    // in exists for: the season under the finger is read late, and the band is standing still by
    // the time it answers.
    travelling.value = false
    await nextTick()
    held.value = items
    await nextTick()

    expect(opened(wrapper, 0)).toContain("slice--open")
  })

  it("leaves the change after them to open from nothing, those slices having spent the memory", async () => {
    const {wrapper, held, travelling} = carriedIn()
    held.value = items
    await nextTick()
    travelling.value = false
    await nextTick()

    // A season re-answered, with nothing in common with the set before it: the visitor's own
    // change, which opens from nothing as it always has.
    held.value = [{id: 3, title: "BS Tempra", meta: "1 on the roster", banner: ""}]
    await nextTick()
    expect(opened(wrapper, 0)).not.toContain("slice--open")

    await settled()
    expect(opened(wrapper, 0)).toContain("slice--open")
  })

  /**
   * A banner covers its slice, so a slice taller than its share of the row is wide is filled
   * by its height and the picture is painted past both edges. jsdom lays nothing out, so the
   * height every other test reads is nothing at all, which is why they still read the share
   * of the row, and why this one has to say how tall the row is before it can ask.
   */
  it("asks for the width the height demands, where the slice is tall and narrow", async () => {
    const laid = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "clientHeight")
    Object.defineProperty(HTMLElement.prototype, "clientHeight", {configurable: true, value: 352})
    try {
      const wrapper = mount(SliceBand, {
        props: {
          items: [
            {id: 1, title: "One", meta: "", banner: "/a.jpg", width: 1920, height: 1080},
            {id: 2, title: "Two", meta: "", banner: "/b.jpg", width: 1920, height: 1080},
          ],
          accent: "#ff4655",
          testidPrefix: "team-roster",
        },
      })
      await settled()
      const slices = wrapper.findAll("section")

      await slices[1].find("img").trigger("load")

      // Shut, its share of a 1024 row is 233, and covering 233 by 352 draws it 626 wide.
      expect(slices[1].find("img").attributes("sizes")).toBe("626px")
    } finally {
      if (laid) Object.defineProperty(HTMLElement.prototype, "clientHeight", laid)
    }
  })
})
