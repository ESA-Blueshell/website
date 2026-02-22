import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import FooterBanner from "@/components/common/banners/FooterBanner.vue"

describe("FooterBanner", () => {
  it("renders desktop and mobile variants with all expected links", () => {
    const desktop = mount(FooterBanner, {
      global: {
        mocks: {
          $vuetify: {
            display: {
              mdAndUp: true,
              smAndDown: false,
              sm: false,
              xs: false,
            },
          },
        },
      },
    })
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

    const mobile = mount(FooterBanner, {
      global: {
        mocks: {
          $vuetify: {
            display: {
              mdAndUp: false,
              smAndDown: true,
              sm: true,
              xs: false,
            },
          },
        },
      },
    })
    expect(mobile.text()).toContain("SITECIE GANG")
    const mobileHtml = mobile.html()
    expect(mobileHtml).toContain("https://marketingmaatwerk.nl/")
    expect(mobileHtml).toContain("https://www.esportsloungetwente.nl/")
  })
})
