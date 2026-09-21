import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import IslandTextarea from "@/components/island/IslandTextarea.vue"

const box = (props: Record<string, unknown> = {}) =>
  mount(IslandTextarea, {props: {modelValue: "", ...props}})

describe("IslandTextarea", () => {
  it("reports what is written in it", async () => {
    const wrapper = box()

    await wrapper.find("textarea").setValue("Two lines\nof it")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("Two lines\nof it")
  })

  it("says it is wrong when it is, and says nothing when it is not", () => {
    expect(box({invalid: true}).classes()).toContain("island-textarea--wrong")
    expect(box().classes()).not.toContain("island-textarea--wrong")
  })

  it("carries the field's own id and the line under it", () => {
    const wrapper = box({controlId: "about", describedBy: "about-said", rows: 8})

    expect(wrapper.attributes("id")).toBe("about")
    expect(wrapper.attributes("aria-describedby")).toBe("about-said")
    expect(wrapper.attributes("rows")).toBe("8")
  })

  it("is off when the form is", () => {
    expect(box({disabled: true, placeholder: "Say something"}).attributes("disabled")).toBeDefined()
    expect(box({placeholder: "Say something"}).attributes("placeholder")).toBe("Say something")
  })
})
