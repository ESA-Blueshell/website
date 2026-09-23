import {describe, expect, it} from "vitest"
import Home from "@/pages/Home.vue"
import router from "@/plugins/router"
import {mountInApp} from "./helpers"

const mountHome = () => mountInApp(Home, {
  global: {
    stubs: {
      HomeHero: true,
      UpcomingBand: true,
      CasualBand: true,
      LineupBand: true,
      DiscordBand: true,
    },
  },
})

describe("Home page", () => {
  it("runs its bands in the order the page reads", () => {
    const wrapper = mountHome()

    const order = ["HomeHero", "UpcomingBand", "CasualBand", "LineupBand", "PerkBand", "DiscordBand", "PartnerWall", "CallBand"]
    const drawn = order.map(name => wrapper.findComponent({name}).element)
    for (let at = 1; at < drawn.length; at++) {
      expect(drawn[at - 1]!.compareDocumentPosition(drawn[at]!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    }
  })

  it("says what membership gets somebody in six ticked points, with the way in beside the heading", () => {
    const perks = mountHome().findComponent({name: "PerkBand"})

    expect(perks.props()).toMatchObject({mark: "tick", columns: 3, heading: "What membership gets you"})
    expect(perks.props("perks").map((one: {title: string}) => one.title)).toEqual([
      "Every event", "The entire Discord", "Casual gaming", "Competitive gaming", "A welcoming community", "Our merch",
    ])
    expect(perks.find("[data-testid=home-perks-signup]").exists()).toBe(true)
  })

  it("links every partner to a page the router knows or to its own site", () => {
    const partners = mountHome().findComponent({name: "PartnerWall"}).props("partners") as Array<{href: string}>

    expect(partners.map(one => one.href)).toEqual([
      "/partners/el-nino", "/partners/marketing-maatwerk", "https://esportsteamtwente.nl/",
    ])
    for (const href of partners.map(one => one.href).filter(one => one.startsWith("/"))) {
      expect(router.resolve(href).matched.length).toBeGreaterThan(0)
    }
  })

  it("closes on the call to become a member or to ask first", () => {
    const call = mountHome().findComponent({name: "CallBand"})

    expect(call.props("actions").map((one: {href: string}) => one.href))
      .toEqual(["/membership/signup", "https://discord.gg/23YMFQy"])
  })
})
