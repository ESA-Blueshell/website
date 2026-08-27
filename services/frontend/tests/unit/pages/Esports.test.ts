import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import Esports from "@/pages/Esports.vue"

const mountPage = () =>
  mount(Esports, {
    global: {
      stubs: {
        RouterLink: {
          props: ["to"],
          template: "<a :data-to='to'><slot /></a>",
        },
      },
    },
  })

describe("Esports page", () => {
  it("lists all configured game route targets", () => {
    // Mounted rather than shallow: the cards are inside an animation wrapper,
    // and a stubbed wrapper renders none of its slot, so a shallow mount would
    // find nothing and say nothing.
    const wrapper = mountPage()

    const targets = wrapper.findAll("a[data-to]").map((node) => node.attributes("data-to"))
    expect(targets).toEqual([
      "/esports/league-of-legends",
      "/esports/counter-strike-2",
      "/esports/valorant",
      "/esports/rocketleague",
      "/esports/geoguessr",
    ])
  })

  it("names every game it links to", () => {
    const wrapper = mountPage()

    expect(wrapper.text()).toContain("League of Legends")
    expect(wrapper.text()).toContain("GeoGuessr")
  })

  it("sits inside the esports island", () => {
    // The island's root is what its reset and its tokens hang off; a page that
    // renders outside it gets neither.
    expect(mountPage().find('[data-testid="esports-island"]').exists()).toBe(true)
  })
})
