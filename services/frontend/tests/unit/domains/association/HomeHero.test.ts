import {describe, expect, it} from "vitest"
import {mount, RouterLinkStub} from "@vue/test-utils"
import HomeHero from "@/domains/association/island/HomeHero.vue"

const mountHero = (props: {online?: number} = {}) =>
  mount(HomeHero, {props, global: {stubs: {RouterLink: RouterLinkStub}}})

describe("HomeHero", () => {
  it("stays dark over its photograph whatever the theme", () => {
    const root = mountHero().find("[data-testid=home-hero]")

    expect(root.classes()).toContain("island-dark")
    expect(root.find("img").attributes("src")).toBe("/banner.webp")
  })

  it("names the association and says where it is", () => {
    const wrapper = mountHero()

    expect(wrapper.find("#blueshell").text()).toBe("Blueshell")
    expect(wrapper.text()).toContain("Student esports and gaming association of the Twente region")
  })

  it("offers membership on the site and the Discord off it", () => {
    const wrapper = mountHero()

    expect(wrapper.findComponent(RouterLinkStub).props("to")).toBe("/membership/signup")
    const discord = wrapper.find("[data-testid=home-join-discord]")
    expect(discord.attributes("href")).toBe("http://localhost:3000/api/discord/invite/welcome")
    expect(discord.attributes("target")).toBe("_blank")
  })

  it("draws the social row as named, filled marks, mail opening the mail app", () => {
    const socials = mountHero().findAll(".home-hero__social")

    expect(socials.map(one => one.attributes("aria-label")))
      .toEqual(["Discord", "Instagram", "Twitch", "LinkedIn", "Facebook", "X", "Email the board"])
    expect(socials.every(one => one.find("svg").attributes("fill") === "currentColor")).toBe(true)
    expect(socials.at(-1)!.attributes("target")).toBeUndefined()
    expect(socials[1]!.attributes("rel")).toBe("noopener")
  })

  it("lights the Discord mark's slot only once there is a count to stand for", () => {
    expect(mountHero().find("[data-testid=home-discord-live]").exists()).toBe(false)

    const counted = mountHero({online: 42})
    expect(counted.find("[data-testid=home-discord-live]").exists()).toBe(true)
    expect(counted.find(".home-hero__social").attributes("aria-label")).toBe("Discord, 42 online")
  })
})
