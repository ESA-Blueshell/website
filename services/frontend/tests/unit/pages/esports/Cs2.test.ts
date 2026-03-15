import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Cs2 from "@/pages/esports/Cs2.vue"

describe("Cs2 page", () => {
  it("defines at least one CS2 team", () => {
    const wrapper = shallowMount(Cs2, {
      global: {
        stubs: {
          TeamDetails: true,
        },
      },
    })

    expect((wrapper.vm as any).teams.length).toBeGreaterThan(0)
    expect((wrapper.vm as any).teams[0].name).toContain("BS")
  })
})
