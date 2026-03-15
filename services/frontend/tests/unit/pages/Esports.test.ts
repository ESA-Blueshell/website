import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Esports from "@/pages/Esports.vue"

describe("Esports page", () => {
  it("lists all configured game route targets", () => {
    const wrapper = shallowMount(Esports, {
      global: {
        stubs: {
          RouterLink: {
            props: ["to"],
            template: "<a :data-to='to'><slot /></a>",
          },
        },
      },
    })

    const targets = wrapper.findAll("a[data-to]").map((node) => node.attributes("data-to"))
    expect(targets).toEqual([
      "/esports/league-of-legends",
      "/esports/counter-strike-2",
      "/esports/valorant",
      "/esports/rocketleague",
      "/esports/geoguessr",
    ])
  })
})
