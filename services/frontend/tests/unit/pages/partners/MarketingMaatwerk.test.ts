import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import MarketingMaatwerk from "@/pages/partners/MarketingMaatwerk.vue"
import {hrefs} from "../helpers"

describe("MarketingMaatwerk page", () => {
  it("renders contact and service links with computed phone href", () => {
    const wrapper = mount(MarketingMaatwerk)
    const links = hrefs(wrapper)
    const pillarHrefs = (wrapper.vm as any).pillars.map((pillar: { href: string }) => pillar.href)

    expect(links).toContain("https://marketingmaatwerk.nl/")
    expect(links).toContain("mailto:info@marketingmaatwerk.nl")
    expect(links).toContain("tel:+31634218964")
    expect(links).toContain("https://marketingmaatwerk.nl/contact/")
    expect(pillarHrefs).toContain("https://marketingmaatwerk.nl/website-maatwerk/")
    expect(pillarHrefs).toContain("https://marketingmaatwerk.nl/seo/")
    expect(pillarHrefs).toContain("https://marketingmaatwerk.nl/webhosting/")
  })
})
