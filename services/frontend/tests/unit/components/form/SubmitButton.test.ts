import {afterEach, describe, expect, it} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {unmountAll} from "../../helpers/testUtils"

describe("SubmitButton", () => {
  const wrappers: VueWrapper[] = []

  function mounted(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
    const wrapper = mount(SubmitButton, {props, slots})
    wrappers.push(wrapper)
    return wrapper
  }

  afterEach(() => unmountAll(wrappers, "SubmitButton"))

  it("says Submit unless told otherwise, and what its slot says over that", () => {
    expect(mounted().text()).toBe("Submit")
    expect(mounted({text: "Create user"}).text()).toBe("Create user")
    expect(mounted({}, {default: "Save changes"}).text()).toBe("Save changes")
  })

  it("is the solid cut button, and sends its form only when it is a submit", () => {
    const button = mounted().get("button")
    expect(button.classes()).toContain("island-cut--solid")
    expect(button.attributes("type")).toBe("button")
    expect(mounted({type: "submit"}).get("button").attributes("type")).toBe("submit")
  })

  it("says it is working and takes no presses while it saves", async () => {
    const wrapper = mounted({loading: true, workingText: "Creating"})
    expect(wrapper.text()).toBe("Creating")
    expect(wrapper.get("button").attributes("disabled")).toBeDefined()
    expect(mounted({loading: true}).text()).toBe("Saving")
  })

  it("takes no presses while disabled, and reports a press otherwise", async () => {
    expect(mounted({disabled: true}).get("button").attributes("disabled")).toBeDefined()
    const wrapper = mounted()
    await wrapper.get("button").trigger("click")
    expect(wrapper.emitted("click")).toHaveLength(1)
  })
})
