import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Geoguessr from "@/pages/esports/Geoguessr.vue"

describe("Geoguessr page", () => {
  it("defines geoguessr teams", () => {
    const wrapper = shallowMount(Geoguessr, {
      global: {
        stubs: {
          TeamDetails: true,
        },
      },
    })

    expect((wrapper.vm as any).teams.length).toBeGreaterThan(0)
  })
})
