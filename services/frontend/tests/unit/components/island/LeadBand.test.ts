import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import LeadBand from "@/components/island/LeadBand.vue"

const band = (props: Record<string, unknown> = {}) =>
  mount(LeadBand, {props, slots: {default: "<h2>What membership gets you</h2>"}})

describe("LeadBand", () => {
  it("lays the wash in the accent it was given", () => {
    expect(band({accent: "#ff4655"}).attributes("style")).toContain("--accent: #ff4655")
  })

  it("takes the association's blue where a section has no colour of its own", () => {
    expect(band().attributes("style")).toContain("--accent: var(--color-brand)")
  })

  it("holds what the band says", () => {
    expect(band().text()).toBe("What membership gets you")
  })
})
