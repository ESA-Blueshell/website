import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Geoguessr from "@/pages/esports/Geoguessr.vue"

describe("Geoguessr page", () => {
  it("asks the shared page for its own game, and keeps its own copy", () => {
    const wrapper = shallowMount(Geoguessr, {
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
    expect(root.attributes("data-game")).toBe("GEOGUESSR")
    expect(root.attributes("data-title")).toBe("Geoguessr")
    expect(root.text()).toContain("geographical knowledge")
  })
})
