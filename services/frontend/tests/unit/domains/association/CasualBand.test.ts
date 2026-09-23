import {afterEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import CasualBand from "@/domains/association/island/CasualBand.vue"

/* The band's own slots are what is under test, so the stand-in draws the details of each. */
const SliceBand = {
  name: "SliceBand",
  props: {items: Array, accent: String, testidPrefix: String, mayAdd: Boolean, addLabel: String, short: Boolean},
  emits: ["add", "go"],
  template: '<div><div v-for="item in items" :key="item.id"><slot name="details" :item="item" /></div></div>',
}

const mountBand = () => mount(CasualBand, {global: {stubs: {SliceBand}}})

afterEach(() => {
  vi.restoreAllMocks()
})

describe("CasualBand", () => {
  it("runs the casual games at banner height, pinned dark, closed by a way to ask for a room", () => {
    const band = mountBand().findComponent({name: "SliceBand"})

    expect(band.props("items").map((one: {title: string}) => one.title))
      .toEqual(["Minecraft", "Dota 2", "Overwatch", "Super Smash Bros", "Trackmania"])
    expect(band.props()).toMatchObject({short: true, mayAdd: true, addLabel: "Ask for a room"})
    expect(band.classes()).toContain("island-dark")
  })

  it("names each game's channel on the way into the Discord", () => {
    const link = mountBand().find("[data-testid=home-casual-link-Minecraft]")

    expect(link.text()).toBe("Open #minecraft on Discord →")
    expect(link.attributes()).toMatchObject({href: "https://discord.gg/23YMFQy", target: "_blank", rel: "noopener"})
  })

  // The link sits on the slice, and a press on it is the link's rather than the slice's.
  it("keeps a press on the link from also choosing the slice", async () => {
    const wrapper = mountBand()
    const chosen = vi.fn()
    wrapper.element.addEventListener("click", chosen)

    await wrapper.find("[data-testid=home-casual-link-Minecraft]").trigger("click")

    expect(chosen).not.toHaveBeenCalled()
  })

  it("opens the Discord beside the site when a slice is followed or a room is asked for", () => {
    const open = vi.spyOn(window, "open").mockReturnValue(null)
    const band = mountBand().findComponent({name: "SliceBand"})

    band.vm.$emit("go")
    band.vm.$emit("add")

    expect(open).toHaveBeenCalledTimes(2)
    expect(open).toHaveBeenCalledWith("https://discord.gg/23YMFQy", "_blank", "noopener")
  })
})
