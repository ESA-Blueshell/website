import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import TopBanner from "@/components/common/banners/TopBanner.vue"

describe("TopBanner", () => {
  it("shows the page's title in capitals", () => {
    const wrapper = mount(TopBanner, {
      props: {title: "Events"},
    })

    expect(wrapper.get("[data-testid='page-banner-title']").text()).toBe("EVENTS")
  })
})
