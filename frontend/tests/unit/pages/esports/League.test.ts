import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import League from "@/pages/esports/League.vue"

describe("League page", () => {
  it("defines league teams", () => {
    const wrapper = shallowMount(League, {
      global: {
        stubs: {
          TeamDetails: true,
        },
      },
    })

    expect((wrapper.vm as any).teams.length).toBeGreaterThan(0)
  })
})
