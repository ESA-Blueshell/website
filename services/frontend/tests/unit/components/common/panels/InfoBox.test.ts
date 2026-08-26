import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import InfoBox from "@/components/common/panels/InfoBox.vue"

const mountBox = (props: Record<string, unknown>, slots: Record<string, string> = {}) =>
  mount(InfoBox, {props: {label: "Rules", ...props}, slots: {default: "<p>the body</p>", ...slots}})

describe("InfoBox", () => {
  it("names what it holds and wears the count of it", () => {
    const box = mountBox({count: 3})

    expect(box.text()).toContain("Rules")
    expect(box.get('[data-testid="info-box-count"]').attributes("content")).toBe("3")
  })

  it("wears no badge when it was given nothing to count", () => {
    const box = mountBox({})

    expect(box.find('[data-testid="info-box-count"]').exists()).toBe(false)
    expect(box.text()).toContain("Rules")
  })

  it("shows its body outright when it is not expandable", () => {
    const box = mountBox({})

    expect(box.find('[data-testid="info-box-body"]').exists()).toBe(true)
    expect(box.find('[data-testid="info-box-toggle"]').exists()).toBe(false)
  })

  it("hides the body behind a chevron when expandable, keeping the heading in view", () => {
    const box = mountBox({expandable: true, count: 1})

    expect(box.find('[data-testid="info-box-body"]').exists()).toBe(false)
    expect(box.text()).toContain("Rules")
    expect(box.find('[data-testid="info-box-toggle"]').exists()).toBe(true)
  })

  it("opens on the header, so the whole row is the target rather than the chevron alone", async () => {
    const box = mountBox({expandable: true})

    await box.get(".info-box__header").trigger("click")

    expect(box.find('[data-testid="info-box-body"]').exists()).toBe(true)
  })

  it("opens from the keyboard", async () => {
    const box = mountBox({expandable: true})

    await box.get(".info-box__header").trigger("keydown.enter")

    expect(box.find('[data-testid="info-box-body"]').exists()).toBe(true)
  })

  it("starts open when asked to", () => {
    const box = mountBox({expandable: true, defaultOpen: true})

    expect(box.find('[data-testid="info-box-body"]').exists()).toBe(true)
  })

  it("says which box the chevron belongs to", () => {
    const box = mountBox({expandable: true})

    expect(box.get('[data-testid="info-box-toggle"]').attributes("aria-label")).toBe("Show Rules")
  })

  it("keeps a click on its actions from opening it", async () => {
    const box = mountBox({expandable: true}, {actions: '<button type="button">Add</button>'})

    await box.get(".info-box__actions button").trigger("click")

    // The action ran; the box did not open under it.
    expect(box.find('[data-testid="info-box-body"]').exists()).toBe(false)
  })

  it("is not a button when there is nothing to open", () => {
    const box = mountBox({})

    expect(box.get(".info-box__header").attributes("role")).toBeUndefined()
  })
})
