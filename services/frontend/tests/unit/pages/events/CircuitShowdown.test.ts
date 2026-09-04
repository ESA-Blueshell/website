import {describe, expect, it} from "vitest"
import CircuitShowdown from "@/pages/events/CircuitShowdown.vue"
import {mountInApp} from "../helpers"

describe("CircuitShowdown page", () => {
  it("contains seeded group and playoff data", () => {
    const wrapper = mountInApp(CircuitShowdown)

    expect((wrapper.vm as any).groupAScores).toHaveLength(3)
    expect((wrapper.vm as any).groupBScores).toHaveLength(3)
    expect((wrapper.vm as any).playoffs).toHaveLength(3)
    expect(wrapper.text()).toContain("Circuit Showdown")
  })
})
