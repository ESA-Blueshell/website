import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import SocialsBanner from "@/components/common/banners/SocialsBanner.vue"

describe("SocialsBanner", () => {
  it("renders social links", () => {
    const wrapper = mount(SocialsBanner, {
      global: {
        mocks: {
          $vuetify: {
            theme: {
              global: {name: "light"},
              computedThemes: {
                light: {
                  colors: {wallpaper: "#111"},
                },
              },
            },
          },
        },
      },
    })

    expect(wrapper.text()).toContain("Follow us on Social Media")
    expect(wrapper.findAll("v-btn").length).toBe(5)
  })
})
