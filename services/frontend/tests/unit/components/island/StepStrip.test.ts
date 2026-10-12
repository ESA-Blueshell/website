import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import StepStrip from "@/components/island/StepStrip.vue"

const strip = (current: number) => mount(StepStrip, {
  props: {steps: ["Your details", "Address", "Membership"], current, testid: "signup-steps"},
})

describe("StepStrip", () => {
  it("lists the steps in order and says which one is in hand", () => {
    const steps = strip(1).get("[data-testid='signup-steps']").findAll("li")

    expect(steps.map((step) => step.find(".steps__name").text())).toEqual(["Your details", "Address", "Membership"])
    expect(steps.map((step) => step.attributes("aria-current"))).toEqual([undefined, "step", undefined])
  })

  it("ticks the steps behind and numbers the ones still to come", () => {
    const steps = strip(1).findAll("li")

    expect(steps[0]!.find("svg").attributes("aria-hidden")).toBe("true")
    expect(steps[0]!.find(".steps__n").exists()).toBe(false)
    expect(steps[1]!.find(".steps__n").text()).toBe("2")
    expect(steps[2]!.find(".steps__n").text()).toBe("3")
    expect(steps[2]!.find("svg").exists()).toBe(false)
  })

  it("makes room for as many steps as it is given", () => {
    const wrapper = mount(StepStrip, {props: {steps: ["One", "Two", "Three", "Four"], current: 0}})

    expect(wrapper.attributes("style")).toContain("--steps: 4")
    expect(wrapper.attributes("data-testid")).toBeUndefined()
  })
})
