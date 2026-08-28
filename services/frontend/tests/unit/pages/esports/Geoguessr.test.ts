import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Geoguessr from "@/pages/esports/Geoguessr.vue"

describe("Geoguessr page", () => {
  it("asks the shared page for its own game, and says nothing else about it", () => {
    // The name, the copy and the art are the record's to give; this says which game it is.
    const wrapper = shallowMount(Geoguessr, {
      global: {
        stubs: {EsportsGamePage: {props: ["game"], template: "<div :data-game='game' />"}},
      },
    })

    expect(wrapper.get("[data-game]").attributes("data-game")).toBe("GEOGUESSR")
  })
})
