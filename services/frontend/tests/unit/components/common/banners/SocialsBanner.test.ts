import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import SocialsBanner from "@/components/common/banners/SocialsBanner.vue"

describe("SocialsBanner", () => {
  it("renders social links", () => {
    const wrapper = mount(SocialsBanner)

    expect(wrapper.text()).toContain("Follow us on Social Media")
    expect(wrapper.findAll('a[target="_blank"]')).toHaveLength(5)
  })
})
