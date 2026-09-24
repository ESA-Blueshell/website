import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import DriftRow, {type DriftItem} from "@/components/island/DriftRow.vue"

const ITEMS: DriftItem[] = [
  {id: "DOTA_2", title: "Dota 2", href: "/casual/dota-2", accent: "#c23c2a", banner: "/dota.webp", srcset: "/dota-640.webp 640w", initials: "D2", sub: "#dota"},
  {id: "OVERWATCH", title: "Overwatch", href: "/casual/overwatch", accent: "#f99e1a", initials: "O"},
]

describe("DriftRow", () => {
  it("names each tile once for a reader, and repeats the row silently for the loop", () => {
    const wrapper = mount(DriftRow, {props: {items: ITEMS, testidPrefix: "olden"}})
    const tiles = wrapper.findAll(".drift-row__tile")

    // Two games, repeated until a pass holds eight tiles, drawn twice for the loop.
    expect(tiles).toHaveLength(16)
    expect(tiles.filter(one => one.attributes("aria-hidden") !== "true")).toHaveLength(2)
    expect(wrapper.get("[data-testid=olden-tile-DOTA_2]").attributes("href")).toBe("/casual/dota-2")
    expect(wrapper.get("[data-testid=olden-tile-DOTA_2]").text()).toContain("#dota")
    expect(wrapper.get("[data-testid=olden-tile-DOTA_2] img").attributes("srcset")).toBe("/dota-640.webp 640w")
    expect(wrapper.get("[data-testid=olden-tile-OVERWATCH] .drift-row__plate").text()).toBe("O")
    expect(tiles[2].attributes("tabindex")).toBe("-1")
  })

  it("hands a pressed tile to its page, and leaves a click with a modifier to the browser", async () => {
    const wrapper = mount(DriftRow, {props: {items: ITEMS, testidPrefix: "olden"}})

    await wrapper.get("[data-testid=olden-tile-OVERWATCH]").trigger("click", {button: 0})
    const opened = new MouseEvent("click", {button: 0, ctrlKey: true, cancelable: true})
    wrapper.get("[data-testid=olden-tile-DOTA_2]").element.dispatchEvent(opened)

    expect(wrapper.emitted("go")).toEqual([[ITEMS[1]]])
    expect(opened.defaultPrevented).toBe(false)
  })

  it("draws nothing without tiles", () => {
    expect(mount(DriftRow, {props: {items: [], testidPrefix: "olden"}}).find(".drift-row").exists()).toBe(false)
  })
})
