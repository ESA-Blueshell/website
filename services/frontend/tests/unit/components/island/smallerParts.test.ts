import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import CutButton from "@/components/island/CutButton.vue"
import IconButton from "@/components/island/IconButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"

describe("the notice", () => {
  it("is a tint of its tone with a title and what it says, and no bar", () => {
    const wrapper = mount(NoticeBox, {props: {tone: "warning", title: "Heads up", testid: "n"}, slots: {default: "Existing sign-ups are kept."}})

    expect(wrapper.classes()).toEqual(expect.arrayContaining(["notice", "notice--warning"]))
    expect(wrapper.find(".notice__title").text()).toBe("Heads up")
    expect(wrapper.find(".notice__body").text()).toBe("Existing sign-ups are kept.")
    expect(wrapper.attributes("role")).toBe("alert")
  })

  it("reads as a status in the info tone, and leaves out a title it was not given", () => {
    const wrapper = mount(NoticeBox, {slots: {default: "Saved."}})

    expect(wrapper.classes()).toContain("notice--info")
    expect(wrapper.attributes("role")).toBe("status")
    expect(wrapper.find(".notice__title").exists()).toBe(false)
  })
})

describe("the icon button", () => {
  it("is named for what it does, and says so when pressed", async () => {
    const wrapper = mount(IconButton, {props: {label: "Remove", testid: "rm"}, slots: {default: "<svg />"}})

    expect(wrapper.attributes()).toMatchObject({"aria-label": "Remove", title: "Remove", type: "button"})
    await wrapper.trigger("click")
    expect(wrapper.emitted("click")).toHaveLength(1)
  })

  it("turns red for a removal, and does nothing while off", () => {
    const wrapper = mount(IconButton, {props: {label: "Remove", danger: true, disabled: true}})

    expect(wrapper.classes()).toContain("icon-button--danger")
    expect((wrapper.element as HTMLButtonElement).disabled).toBe(true)
  })
})

describe("the cut button's danger tone", () => {
  it("is drawn in the tone that throws something away", () => {
    expect(mount(CutButton, {props: {tone: "danger"}}).classes()).toContain("island-cut--danger")
  })
})
