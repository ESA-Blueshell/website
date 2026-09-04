import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import AboutUs from "@/pages/AboutUs.vue"

describe("AboutUs page", () => {
  it("renders association history with board-year text", () => {
    const wrapper = mount(AboutUs)

    expect(wrapper.text()).toContain("About us")
    expect(wrapper.text()).toContain("History")
    expect(wrapper.text()).toContain("year of Blueshell's existence")
  })
})
