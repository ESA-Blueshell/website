import {describe, expect, it} from "vitest"
import AboutUs from "@/pages/AboutUs.vue"
import {mountInApp} from "./helpers"

describe("AboutUs page", () => {
  it("renders association history with board-year text", () => {
    const wrapper = mountInApp(AboutUs)

    expect(wrapper.text()).toContain("About us")
    expect(wrapper.text()).toContain("History")
    expect(wrapper.text()).toContain("year of Blueshell's existence")
  })
})
