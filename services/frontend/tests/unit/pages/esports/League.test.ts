import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import League from "@/pages/esports/League.vue"

describe("League of Legends page", () => {
  it("asks the shared page for its own game, and keeps its own copy", () => {
    const wrapper = shallowMount(League, {
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
    expect(root.attributes("data-game")).toBe("LEAGUE_OF_LEGENDS")
    expect(root.attributes("data-title")).toBe("League of Legends")
    expect(root.text()).toContain("special place")
  })
})
