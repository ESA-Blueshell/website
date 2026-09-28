import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import PartnerPage from "@/domains/association/island/PartnerPage.vue"
import {PARTNER_PAGES, type PartnerContent} from "@/domains/association"

const mountPartner = (content: PartnerContent) => mount(PartnerPage, {props: {content}})
const hrefsOf = (wrapper: ReturnType<typeof mountPartner>) =>
  wrapper.findAll("a").map(link => link.attributes("href"))

describe("PartnerPage", () => {
  it("heads the page with the partner's name and its second line", () => {
    const heading = mountPartner(PARTNER_PAGES["el-nino"]).get("h1").text()

    expect(heading).toContain("El Niño")
    expect(heading).toContain("Digital Development")
  })

  it("leads from the logo to the partner's own site, in a new tab", () => {
    const logo = mountPartner(PARTNER_PAGES["marketing-maatwerk"]).get("a[aria-label='Visit Marketing Maatwerk']")

    expect(logo.attributes()).toMatchObject({href: "https://marketingmaatwerk.nl/", target: "_blank"})
    expect(logo.get("img").classes()).toContain("partner__logo--inverted")
  })

  it("lists a fact as text, or as a link where it has somewhere to go", () => {
    const wrapper = mountPartner(PARTNER_PAGES["el-nino"])
    expect(wrapper.find("dl a").exists()).toBe(false)
    expect(wrapper.get("dl").text()).toContain("Kuipersdijk 6C")

    const mail = mountPartner(PARTNER_PAGES["marketing-maatwerk"]).get("a[href='mailto:info@marketingmaatwerk.nl']")
    expect(mail.attributes("target")).toBeUndefined()
  })

  it("keeps every outbound link the two hand-built pages had", () => {
    expect(hrefsOf(mountPartner(PARTNER_PAGES["el-nino"]))).toEqual(expect.arrayContaining([
      "https://www.elnino.tech/vacatures", "https://www.elnino.tech/getajob", "https://wa.me/31626978392",
    ]))
    expect(hrefsOf(mountPartner(PARTNER_PAGES["marketing-maatwerk"]))).toEqual(expect.arrayContaining([
      "https://marketingmaatwerk.nl/", "mailto:info@marketingmaatwerk.nl", "tel:+31634218964",
      "https://marketingmaatwerk.nl/contact/", "https://marketingmaatwerk.nl/website-maatwerk/",
      "https://marketingmaatwerk.nl/seo/", "https://marketingmaatwerk.nl/webhosting/",
    ]))
  })

  it("draws a section's offers only where it has them", () => {
    const sections = mountPartner(PARTNER_PAGES["marketing-maatwerk"]).findAll(".partner__section")

    expect(sections.map(section => section.find(".partner__offers").exists())).toEqual([false, true, false, false])
  })

  it("leaves the heading one line for a partner without a tagline", () => {
    const heading = mountPartner(PARTNER_PAGES["marketing-maatwerk"]).get("h1")

    expect(heading.find("br").exists()).toBe(false)
  })
})
