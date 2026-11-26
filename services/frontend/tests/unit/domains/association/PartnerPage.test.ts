import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import PartnerPage from "@/domains/association/island/PartnerPage.vue"
import {PARTNER_PAGES, type PartnerContent} from "@/domains/association"

const mountPartner = (content: PartnerContent) => mount(PartnerPage, {props: {content}})
const hrefsOf = (wrapper: ReturnType<typeof mountPartner>) =>
  wrapper.findAll("a").map(link => link.attributes("href"))

/** A partner with linked facts, offers and no tagline: shapes no shipped page has today. */
const SAMPLE: PartnerContent = {
  name: "Sample Partner",
  logo: {src: "/sample.webp", alt: "Sample Partner logo", invertInDark: false},
  site: "https://sample.example/",
  facts: {
    heading: "Contact",
    items: [{label: "Email", value: "info@sample.example", href: "mailto:info@sample.example"}],
  },
  sections: [
    {heading: "About", paragraphs: ["What the partner is."]},
    {
      heading: "What they do",
      paragraphs: ["Their services:"],
      offers: [{title: "Websites", body: "Built to order.", href: "https://sample.example/websites/"}],
    },
  ],
  actions: [{label: "Visit website", href: "https://sample.example/"}],
}

describe("PartnerPage", () => {
  it("heads the page with the partner's name and its second line", () => {
    const heading = mountPartner(PARTNER_PAGES["el-nino"]).get("h1").text()

    expect(heading).toContain("El Niño")
    expect(heading).toContain("Digital Development")
  })

  it("leads from the logo to the partner's own site, in a new tab", () => {
    const logo = mountPartner(PARTNER_PAGES["el-nino"]).get("a[aria-label='Visit El Niño']")

    expect(logo.attributes()).toMatchObject({href: "https://www.elnino.tech/", target: "_blank"})
    expect(logo.get("img").classes()).toContain("partner__logo--inverted")
  })

  it("lists a fact as text, or as a link where it has somewhere to go", () => {
    const wrapper = mountPartner(PARTNER_PAGES["el-nino"])
    expect(wrapper.find("dl a").exists()).toBe(false)
    expect(wrapper.get("dl").text()).toContain("Kuipersdijk 6C")

    const mail = mountPartner(SAMPLE).get("a[href='mailto:info@sample.example']")
    expect(mail.attributes("target")).toBeUndefined()
  })

  it("keeps every outbound link the hand-built page had", () => {
    expect(hrefsOf(mountPartner(PARTNER_PAGES["el-nino"]))).toEqual(expect.arrayContaining([
      "https://www.elnino.tech/vacatures", "https://www.elnino.tech/getajob", "https://wa.me/31626978392",
    ]))
  })

  it("draws a section's offers only where it has them", () => {
    const sections = mountPartner(SAMPLE).findAll(".partner__section")

    expect(sections.map(section => section.find(".partner__offers").exists())).toEqual([false, true])
    expect(hrefsOf(mountPartner(SAMPLE))).toContain("https://sample.example/websites/")
  })

  it("leaves the heading one line for a partner without a tagline", () => {
    const heading = mountPartner(SAMPLE).get("h1")

    expect(heading.find("br").exists()).toBe(false)
  })
})
