import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import FooterBanner from "@/components/common/banners/FooterBanner.vue"

describe("FooterBanner", () => {
  it("renders desktop and mobile variants", () => {
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
  })
})
