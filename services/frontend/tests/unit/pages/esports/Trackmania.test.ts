import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Trackmania from "@/pages/esports/Trackmania.vue"

describe("Trackmania page", () => {
  it("asks the shared page for its own game, and keeps its own copy", () => {
    const wrapper = shallowMount(Trackmania, {
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
    expect(root.attributes("data-game")).toBe("TRACKMANIA")
    expect(root.attributes("data-title")).toBe("Trackmania")
    expect(root.text()).toContain("Blueshell banner")
  })
})
