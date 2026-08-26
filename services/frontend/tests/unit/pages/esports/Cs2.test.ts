import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Cs2 from "@/pages/esports/Cs2.vue"

describe("Counter-Strike 2 page", () => {
  it("asks the shared page for its own game, and keeps its own copy", () => {
    const wrapper = shallowMount(Cs2, {
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
    expect(root.attributes("data-game")).toBe("CS2")
    expect(root.attributes("data-title")).toBe("Counter-Strike 2")
    expect(root.text()).toContain("climb up the charts")
  })
})
