import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import BannerSlices from "@/domains/esports/island/BannerSlices.vue"

const items = [
  {id: 1, title: "BS Waterboarders", meta: "5 on the roster", banner: "/a.jpg"},
  {id: 2, title: "BS SpicyWater", meta: "2 on the roster", banner: ""},
]

const mountSlices = () =>
  mount(BannerSlices, {
    props: {items, accent: "#ff4655", testidPrefix: "team-roster"},
    slots: {details: '<span class="detail">{{ params.item.title }} roster</span>'},
  })

const settled = () => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))

describe("BannerSlices", () => {
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
    expect(slices[0].classes()).toContain("team-slice--open")
    expect(slices[1].classes()).not.toContain("team-slice--open")
    expect(slices[0].get("button").attributes("aria-expanded")).toBe("true")
  })

  it("opens the slice under the pointer instead", async () => {
    const wrapper = mountSlices()
    await settled()
    const slices = wrapper.findAll("section")

    await slices[1].trigger("mouseenter")

    expect(slices[1].classes()).toContain("team-slice--open")
    expect(slices[0].classes()).not.toContain("team-slice--open")
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
   * A slice is a tall narrow strip whose banner covers it, so the picture is drawn far wider
   * than the strip: the strip's own width would fetch something blurred. The figure is asked
   * for twice — an understated guess that gets a small copy on the screen, then the width the
   * band was actually laid out at.
   */
  it("asks for a small copy before the band has been laid out", () => {
    const wrapper = mountSlices()

    // No layout has happened, so there is nothing to measure and nothing to wait for.
    expect(wrapper.find("img").attributes("sizes")).toBe("(min-width: 768px) 200px, 50vw")
  })

  it("asks for the width it is really drawn at once the band has been laid out", async () => {
    const wrapper = mountSlices()
    // jsdom lays nothing out, so the box a slice would have is stood in for here. 200 wide by
    // 352 tall is a collapsed slice on a desktop band.
    wrapper.findAll("section").forEach(slice => {
      slice.element.getBoundingClientRect = () => ({width: 200, height: 352}) as DOMRect
    })

    await settled()
    await settled()

    // A 16x9 banner covering a 200x352 box is drawn 626 wide, and the open slice is the one
    // not held at 1.06 — so it asks for the honest 626 rather than the strip's 200.
    expect(wrapper.find("img").attributes("sizes")).toBe("626px")
  })

  it("asks for more as a slice opens, and never for less once it has shut again", async () => {
    // Both slices carry a picture here, because the point is which of the two is asked for
    // more — the shared fixture gives the second one none.
    const wrapper = mount(BannerSlices, {
      props: {
        items: [
          {id: 1, title: "One", meta: "", banner: "/a.jpg"},
          {id: 2, title: "Two", meta: "", banner: "/b.jpg"},
        ],
        accent: "#ff4655",
        testidPrefix: "team-roster",
      },
    })
    const slices = wrapper.findAll("section")
    const asked = (index: number) =>
      Number(slices[index].find("img").attributes("sizes")!.replace("px", ""))

    // The second slice is a narrow strip until it opens, and takes the larger share of the row
    // when it does. jsdom lays nothing out, so its box is stood in for and grown by hand.
    let second = 200
    slices[0].element.getBoundingClientRect = () => ({width: 200, height: 352}) as DOMRect
    slices[1].element.getBoundingClientRect = () => ({width: second, height: 352}) as DOMRect

    await settled()
    await settled()
    // Held at 1.06 while shut, so a shut slice asks for a little more than the honest 626.
    expect(asked(1)).toBe(664)

    second = 900
    await slices[1].trigger("mouseenter")
    await settled()
    expect(asked(1)).toBe(900)

    // Shut again, and still asked for the copy it already has: a browser will not swap a
    // picture for a smaller one, so asking for less would achieve nothing.
    second = 200
    await slices[0].trigger("mouseenter")
    await settled()
    expect(asked(1)).toBe(900)
  })
})
