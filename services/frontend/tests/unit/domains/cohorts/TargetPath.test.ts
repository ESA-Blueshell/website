import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import TargetPath from "@/domains/cohorts/components/TargetPath.vue"

const mountPath = (props: {path?: string[] | null; leaf?: string | null}) =>
  mount(TargetPath, {props})

/** The steps as they read, separators dropped — the gap between them is drawn, not typed. */
const stepsOf = (wrapper: ReturnType<typeof mountPath>) =>
  wrapper.findAll('[data-testid="target-path"] > span')
    .filter(step => !step.classes("target-path__separator"))
    .map(step => step.text())

describe("TargetPath", () => {
  it("reads outside in, from the system down to the target", () => {
    const wrapper = mountPath({path: ["Brevo", "Periods"], leaf: "Members 2025-2026"})

    expect(stepsOf(wrapper)).toEqual(["Brevo", "Periods", "Members 2025-2026"])
  })

  it("separates every step but the first, so the path never opens with an arrow", () => {
    const wrapper = mountPath({path: ["Brevo", "Periods"], leaf: "Members 2025-2026"})

    const separators = wrapper.findAll(".target-path__separator")
    expect(separators).toHaveLength(2)
    expect(separators.every(separator => separator.text() === "›")).toBe(true)
    expect(wrapper.get('[data-testid="target-path"]').element.firstElementChild?.className)
      .not.toContain("separator")
  })

  it("marks the target itself, not the folders it sits in", () => {
    const wrapper = mountPath({path: ["Brevo", "Periods"], leaf: "Members 2025-2026"})

    const leaves = wrapper.findAll(".target-path__leaf")
    expect(leaves).toHaveLength(1)
    expect(leaves[0].text()).toBe("Members 2025-2026")
  })

  it("shows a target at the top level without an empty step", () => {
    const wrapper = mountPath({path: ["Brevo"], leaf: "Loose ends"})

    expect(stepsOf(wrapper)).toEqual(["Brevo", "Loose ends"])
  })

  it("carries as many steps as a system that nests deeply reports", () => {
    const wrapper = mountPath({path: ["Discord", "Blueshell", "Committees"], leaf: "Web Cmte"})

    expect(stepsOf(wrapper)).toEqual(["Discord", "Blueshell", "Committees", "Web Cmte"])
  })

  it("renders nothing at all when a system reports no location", () => {
    const wrapper = mountPath({path: [], leaf: null})

    expect(wrapper.find('[data-testid="target-path"]').exists()).toBe(false)
  })

  it("drops a blank step rather than printing a gap", () => {
    // A folder recorded as an empty string is not a folder.
    const wrapper = mountPath({path: ["Brevo", "  "], leaf: "Members"})

    expect(stepsOf(wrapper)).toEqual(["Brevo", "Members"])
  })
})
