import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick} from "vue"
import FooterBanner from "@/components/common/banners/FooterBanner.vue"

describe("FooterBanner", () => {
  it("renders desktop and mobile variants with all expected links", async () => {
    // The banner is behind a `v-lazy`, so its body arrives a tick after the mount.
    const desktop = mount(FooterBanner)
    await nextTick()
    expect(desktop.text()).toContain("SITECIE GANG")
    const desktopHtml = desktop.html()
    expect(desktopHtml).toContain("mailto:board@blueshell.utwente.nl")
    expect(desktopHtml).toContain("https://www.instagram.com/esablueshell/")
    expect(desktopHtml).toContain("https://www.facebook.com/BlueshellEsports/")
    expect(desktopHtml).toContain("https://www.twitch.tv/blueshellesports")
    expect(desktopHtml).toContain("https://twitter.com/BlueshellESA")
    expect(desktopHtml).toContain("https://www.linkedin.com/company/blueshell-esports")
    expect(desktopHtml).toContain("https://www.elnino.tech/")
    expect(desktopHtml).toContain("https://marketingmaatwerk.nl/")
    expect(desktopHtml).toContain("https://esportsteamtwente.nl/")
    expect(desktopHtml).toContain("https://www.esportsloungetwente.nl/")

    // The banner forks on the breakpoint, and the breakpoint is read off the window, so a
    // phone is a narrower window rather than a description of one. 700 lands in `sm`.
    globalThis.innerWidth = 700
    globalThis.dispatchEvent(new Event("resize"))
    await nextTick()

    const mobile = mount(FooterBanner)
    await nextTick()
    expect(mobile.text()).toContain("SITECIE GANG")
    const mobileHtml = mobile.html()
    expect(mobileHtml).toContain("https://marketingmaatwerk.nl/")
    expect(mobileHtml).toContain("https://www.esportsloungetwente.nl/")
  })
})
