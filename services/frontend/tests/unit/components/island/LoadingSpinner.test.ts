import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import LoadingSpinner from "@/components/island/LoadingSpinner.vue"

describe("LoadingSpinner", () => {
  it("tells a screen reader the page is busy, in words", () => {
    const wrapper = mount(LoadingSpinner)

    const status = wrapper.get("[role='status']")
    expect(status.text()).toBe("Loading")
    expect(status.get("svg").attributes("aria-hidden")).toBe("true")
  })

  it("says what is loading when the page knows", () => {
    const wrapper = mount(LoadingSpinner, {props: {label: "Activating your account"}})

    expect(wrapper.get("[role='status']").text()).toBe("Activating your account")
  })

  it("draws at the size it is given, and at 32 pixels otherwise", () => {
    expect(mount(LoadingSpinner).get("svg").attributes("width")).toBe("32")
    expect(mount(LoadingSpinner, {props: {size: 28}}).get("svg").attributes("width")).toBe("28")
  })

  it("keeps its 4 pixel stroke inside the ring at every size", () => {
    const ring = (size: number) => mount(LoadingSpinner, {props: {size}}).get("circle").attributes()

    expect(ring(32)).toMatchObject({r: "14", "stroke-width": "4"})
    expect(ring(28)).toMatchObject({r: "12", "stroke-width": "4"})
  })
})
