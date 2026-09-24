import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import ArtCells, {type ArtCell} from "@/components/island/ArtCells.vue"

const CELLS: ArtCell[] = [
  {id: "VALORANT", title: "Valorant", href: "/casual/valorant", accent: "#ff4655", banner: "/v.webp", srcset: "/v-640.webp 640w", icon: "/v-icon.webp", initials: "V", sub: "#valorant", chips: ["LanCie"]},
  {id: "DOTA_2", title: "Dota 2", href: "/casual/dota-2", accent: "#c23c2a", initials: "D2", archived: true},
]

describe("ArtCells", () => {
  it("draws every cell with its art or its plate, its caption and its tags", () => {
    const wrapper = mount(ArtCells, {props: {cells: CELLS, testidPrefix: "every"}})
    const valorant = wrapper.get("[data-testid=every-cell-VALORANT]")
    const dota = wrapper.get("[data-testid=every-cell-DOTA_2]")

    expect(valorant.attributes("href")).toBe("/casual/valorant")
    expect(valorant.get(".art-cells__banner").attributes("srcset")).toBe("/v-640.webp 640w")
    expect(valorant.get(".art-cells__icon").attributes("src")).toBe("/v-icon.webp")
    expect(valorant.text()).toContain("#valorant")
    expect(valorant.get(".art-cells__chip").text()).toBe("LanCie")
    expect(valorant.find(".art-cells__tag").exists()).toBe(false)
    expect(dota.classes()).toContain("art-cells__cell--old")
    expect(dota.get(".art-cells__plate").text()).toBe("D2")
    expect(dota.get(".art-cells__tag").text()).toBe("Archived")
  })

  it("hands a pressed cell to its page, and leaves a click with a modifier to the browser", async () => {
    const wrapper = mount(ArtCells, {props: {cells: CELLS, testidPrefix: "every"}})

    await wrapper.get("[data-testid=every-cell-DOTA_2]").trigger("click", {button: 0})
    const opened = new MouseEvent("click", {button: 0, shiftKey: true, cancelable: true})
    wrapper.get("[data-testid=every-cell-VALORANT]").element.dispatchEvent(opened)

    expect(wrapper.emitted("go")).toEqual([[CELLS[1]]])
    expect(opened.defaultPrevented).toBe(false)
  })
})
