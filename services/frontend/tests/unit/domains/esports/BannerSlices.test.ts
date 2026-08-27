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
})
