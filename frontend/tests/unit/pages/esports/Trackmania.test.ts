import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Trackmania from "@/pages/esports/Trackmania.vue"

describe("Trackmania page", () => {
  it("defines trackmania teams", () => {
    const wrapper = shallowMount(Trackmania, {
      global: {
        stubs: {
          TeamDetails: true,
        },
      },
    })

    expect((wrapper.vm as any).teams.length).toBeGreaterThan(0)
  })
})
