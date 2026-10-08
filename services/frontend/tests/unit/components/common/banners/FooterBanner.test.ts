import {describe, expect, it} from "vitest"
import {mount, RouterLinkStub} from "@vue/test-utils"
import FooterBanner from "@/components/common/banners/FooterBanner.vue"

const mountFooter = () => mount(FooterBanner, {global: {stubs: {RouterLink: RouterLinkStub}}})

describe("FooterBanner", () => {
  it("stands on its own island root, so it follows the page theme on any page", () => {
    const footer = mountFooter()

    expect(footer.find("footer").classes()).toContain("island")
  })

  it("names every social account it links to", () => {
    const socials = mountFooter().findAll(".site-footer__social")

    expect(socials.map(one => one.attributes("aria-label"))).toEqual([
      "Discord", "Instagram", "Twitch", "LinkedIn", "Facebook", "X", "Email the board",
    ])
    expect(socials.map(one => one.attributes("href"))).toContain("https://www.instagram.com/esablueshell/")
  })

  it("opens an outside account in a tab, and mail in the mail app", () => {
    const socials = mountFooter().findAll(".site-footer__social")
    const discord = socials.find(one => one.attributes("aria-label") === "Discord")!
    const mail = socials.find(one => one.attributes("aria-label") === "Email the board")!

    expect(discord.attributes("target")).toBe("_blank")
    expect(discord.attributes("rel")).toBe("noopener")
    expect(mail.attributes("target")).toBeUndefined()
  })

  it("routes to the site's own pages and links out to the rest", () => {
    const footer = mountFooter()

    const routed = footer.findAllComponents(RouterLinkStub).map(one => one.props("to"))
    expect(routed).toEqual(["/aboutus", "/esports", "/events", "/partners/el-nino", "/partners/marketing-maatwerk"])
    expect(footer.html()).toContain("https://esportsteamtwente.nl/")
    expect(footer.text()).toContain("Ask us on Discord")
  })

  it("credits the committee for this year", () => {
    const text = mountFooter().text()

    expect(text).toContain(`SITECIE GANG © ${new Date().getFullYear()}`)
    expect(text).toContain("JorisJonkers.dev")
  })
})
