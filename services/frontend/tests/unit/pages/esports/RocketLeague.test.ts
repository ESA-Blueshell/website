import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import RocketLeague from "@/pages/esports/RocketLeague.vue"

describe("Rocket League page", () => {
  it("asks the shared page for its own game, and keeps its own copy", () => {
    const wrapper = shallowMount(RocketLeague, {
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
    expect(root.attributes("data-game")).toBe("ROCKET_LEAGUE")
    expect(root.attributes("data-title")).toBe("Rocket League")
    expect(root.text()).toContain("rocket league team")
  })
})
