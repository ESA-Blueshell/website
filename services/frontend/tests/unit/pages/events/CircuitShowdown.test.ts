import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import CircuitShowdown from "@/pages/events/CircuitShowdown.vue"

describe("CircuitShowdown page", () => {
  it("contains seeded group and playoff data", () => {
    const wrapper = mount(CircuitShowdown)

    expect((wrapper.vm as any).groupAScores).toHaveLength(3)
    expect((wrapper.vm as any).groupBScores).toHaveLength(3)
    expect((wrapper.vm as any).playoffs).toHaveLength(3)
    expect(wrapper.text()).toContain("Circuit Showdown")
  })
})
