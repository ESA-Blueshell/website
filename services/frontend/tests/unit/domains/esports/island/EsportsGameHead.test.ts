import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {h} from "vue"
import EsportsGameHead from "@/domains/esports/island/EsportsGameHead.vue"

const stubs = {
  HeaderBand: {setup: (_: unknown, {slots}: {slots: Record<string, () => unknown>}) => () => h("div", [slots["head"]?.(), slots["default"]?.()])},
}

describe("the competition head", () => {
  it("names the game, draws its logo and intro, and carries what the page slots in", () => {
    const wrapper = mount(EsportsGameHead, {
      props: {accent: "#f00", name: "Valorant", icon: "/v.webp", iconSrcset: "/v.webp 128w", intro: "Five a side"},
      slots: {edit: "<a data-testid=edit />", default: "<p data-testid=rail />"},
      global: {stubs},
    })

    expect(wrapper.get("h1").text()).toBe("Valorant")
    expect(wrapper.get("[data-testid=esports-game-icon]").attributes("srcset")).toBe("/v.webp 128w")
    expect(wrapper.get("[data-testid=esports-game-intro]").text()).toContain("Five a side")
    expect(wrapper.find("[data-testid=edit]").exists()).toBe(true)
    expect(wrapper.find("[data-testid=rail]").exists()).toBe(true)
  })

  it("draws no logo or intro the game has none of", () => {
    const wrapper = mount(EsportsGameHead, {props: {accent: "#f00", name: "Chess"}, global: {stubs}})

    expect(wrapper.find("[data-testid=esports-game-icon]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=esports-game-intro]").exists()).toBe(false)
  })
})
