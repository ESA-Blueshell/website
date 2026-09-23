import {describe, expect, it} from "vitest"
import {mount, RouterLinkStub} from "@vue/test-utils"
import PartnerWall from "@/domains/association/island/PartnerWall.vue"

const partners = [
  {name: "Here", href: "/partners/here", light: "/here-light.png", dark: "/here-dark.png", invertInDark: true},
  {name: "Away", href: "https://away.example/", light: "/away.png", dark: "/away.png"},
  {name: "Nowhere", href: null, light: "/n.png", dark: "/n.png"},
]

const mountWall = () => mount(PartnerWall, {
  props: {eyebrow: "In good company", heading: "Our partners", partners, testid: "wall"},
  slots: {default: "<a>More</a>"},
  global: {stubs: {RouterLink: RouterLinkStub}},
})

describe("PartnerWall", () => {
  it("heads the logos with the band's words and its one way on", () => {
    const wrapper = mountWall()

    expect(wrapper.find("h2").text()).toBe("Our partners")
    expect(wrapper.find(".band-head__way").text()).toBe("More")
  })

  it("routes to a partner's page here, opens one elsewhere in a tab, and links nothing without one", () => {
    const wrapper = mountWall()

    expect(wrapper.getComponent(RouterLinkStub).props("to")).toBe("/partners/here")
    expect(wrapper.get("[data-testid=wall-Away]").attributes()).toMatchObject({href: "https://away.example/", target: "_blank", rel: "noopener"})
    expect(wrapper.get("[data-testid=wall-Nowhere]").element.tagName).toBe("DIV")
  })

  it("carries a logo for each half of the theme, named for somebody who cannot see it", () => {
    const logos = mountWall().get("[data-testid=wall-Here]").findAll("img")

    expect(logos.map(one => one.attributes("src"))).toEqual(["/here-light.png", "/here-dark.png"])
    expect(logos.every(one => one.attributes("alt") === "Here")).toBe(true)
    expect(logos[1]!.classes()).toContain("wall__logo--inverted")
    expect(mountWall().get("[data-testid=wall-Away] .wall__logo--dark").classes()).not.toContain("wall__logo--inverted")
  })
})
