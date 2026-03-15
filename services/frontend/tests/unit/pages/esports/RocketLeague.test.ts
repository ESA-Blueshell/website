import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import RocketLeague from "@/pages/esports/RocketLeague.vue"

describe("RocketLeague page", () => {
  it("defines rocket league teams", () => {
    const wrapper = shallowMount(RocketLeague, {
      global: {
        stubs: {
          TeamDetails: true,
        },
      },
    })

    expect((wrapper.vm as any).teams.length).toBeGreaterThan(0)
  })
})
