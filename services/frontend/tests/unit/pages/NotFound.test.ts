import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import NotFound from "@/pages/NotFound.vue"

describe("NotFound page", () => {
  it("renders fallback text and a return link", () => {
    const wrapper = shallowMount(NotFound)

    expect(wrapper.text()).toContain("Uh oh, we made a fucky wucky")
    expect(wrapper.get("a").attributes("href")).toBe("/")
  })
})
