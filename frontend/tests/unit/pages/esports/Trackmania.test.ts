import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Trackmania from "@/pages/esports/Trackmania.vue"

describe("Trackmania page", () => {
  it("mounts even when deprecated content changes", () => {
    const wrapper = shallowMount(Trackmania, {
      global: {
        stubs: {
          TeamDetails: true,
        },
      },
    })

    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain("Trackmania")
    expect(Array.isArray((wrapper.vm as any).teams)).toBe(true)
  })
})
