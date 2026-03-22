import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Valorant from "@/pages/esports/Valorant.vue"

describe("Valorant page", () => {
  it("defines valorant teams", () => {
    const wrapper = shallowMount(Valorant, {
      global: {
        stubs: {
          TeamDetails: true,
        },
      },
    })

    expect((wrapper.vm as any).teams.length).toBeGreaterThan(0)
  })
})
