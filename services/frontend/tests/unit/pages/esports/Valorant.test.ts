import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Valorant from "@/pages/esports/Valorant.vue"

describe("Valorant page", () => {
  it("asks the shared page for its own game, and keeps its own copy", () => {
    const wrapper = shallowMount(Valorant, {
      global: {
        stubs: {
          EsportsGamePage: {
            props: ["game", "title"],
            template: "<div :data-game='game' :data-title='title'><slot name='intro' /></div>",
          },
        },
      },
    })

    const root = wrapper.get("[data-game]")
    expect(root.attributes("data-game")).toBe("VALORANT")
    expect(root.attributes("data-title")).toBe("Valorant")
    expect(root.text()).toContain("dutch Valorant esports scene")
  })
})
