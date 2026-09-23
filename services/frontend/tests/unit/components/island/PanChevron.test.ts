import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import PanChevron from "@/components/island/PanChevron.vue"

const chevron = (props: Record<string, unknown> = {}) =>
  mount(PanChevron, {props: {way: "on", label: "Show what came after", ...props}})

describe("PanChevron", () => {
  it("points the way it moves the set", () => {
    expect(chevron().find("path").attributes("d")).toBe("M9.5 5.5 16 12l-6.5 6.5")
    expect(chevron({way: "back"}).find("path").attributes("d")).toBe("M14.5 5.5 8 12l6.5 6.5")
  })

  it("says what it does, since the glyph says nothing", () => {
    expect(chevron().attributes("aria-label")).toBe("Show what came after")
    expect(chevron().find("svg").attributes("aria-hidden")).toBe("true")
  })

  it("shows the same while the set travels as it does under a pointer", () => {
    expect(chevron().classes()).not.toContain("pan-chevron--live")
    expect(chevron({live: true}).classes()).toContain("pan-chevron--live")
  })

  it("asks for a pan when it is pressed", async () => {
    const wrapper = chevron()

    await wrapper.trigger("click")

    expect(wrapper.emitted("pan")).toHaveLength(1)
  })
})
