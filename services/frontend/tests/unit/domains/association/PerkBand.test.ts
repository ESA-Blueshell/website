import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import PerkBand from "@/domains/association/island/PerkBand.vue"

const perks = [
  {id: "events", title: "Every event", body: "Game nights and LANs."},
  {id: "merch", title: "Our merch", body: "Something new every year."},
]

describe("PerkBand", () => {
  it("marks each claim with the lean, two to a row, where nothing else is asked", () => {
    const wrapper = mount(PerkBand, {props: {heading: "What membership gets you", perks}})

    expect(wrapper.findAll(".perk-band__perk--lean")).toHaveLength(2)
    expect(wrapper.find(".perk-band__tick").exists()).toBe(false)
    expect(wrapper.find("ul").classes()).not.toContain("lg:grid-cols-3")
  })

  it("ticks each claim, three to a row, with the eyebrow and the way in in the head", () => {
    const wrapper = mount(PerkBand, {
      props: {heading: "What membership gets you", perks, eyebrow: "You can be a member too", mark: "tick", columns: 3, testid: "p"},
      slots: {default: "<a>Become a member</a>"},
    })

    expect(wrapper.findAll(".perk-band__tick")).toHaveLength(2)
    expect(wrapper.find("ul").classes()).toContain("lg:grid-cols-3")
    expect(wrapper.find("p").text()).toBe("You can be a member too")
    expect(wrapper.find(".band-head__way").text()).toBe("Become a member")
    // The description sits under the title, inside the same indented block.
    expect(wrapper.get("[data-testid=p-merch] .perk-band__words").text()).toContain("Something new every year.")
  })

  it("stands on the page ground, or washes itself in a colour as a lead band", () => {
    const plain = mount(PerkBand, {props: {heading: "What membership gets you", perks, testid: "p"}})
    const washed = mount(PerkBand, {props: {heading: "What membership gets you", perks, testid: "p", accent: "var(--color-acid)"}})

    expect(plain.find(".lead-band").exists()).toBe(false)
    expect(plain.get("[data-testid=p]").element.tagName).toBe("SECTION")
    expect(washed.get(".lead-band").attributes("style")).toContain("--accent: var(--color-acid)")
    expect(washed.get("[data-testid=p]").classes()).toContain("lead-band")
    expect(washed.findAll(".perk-band__perk")).toHaveLength(2)
  })
})
