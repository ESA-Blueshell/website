import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"

const ModalDialog = {name: "ModalDialog", props: {open: Boolean, title: String, testid: String, danger: Boolean, cancelTestid: String}, template: "<div><slot /><slot name=\"footer\" /></div>"}

describe("ConfirmDialog", () => {
  it("asks in the danger style, and confirms with a press on its own button", async () => {
    const wrapper = mount(ConfirmDialog, {props: {open: true, title: "Delete the list", question: "Delete Sitecie?", confirmLabel: "Delete"}, global: {stubs: {ModalDialog}}})

    expect(wrapper.getComponent(ModalDialog).props("danger")).toBe(true)
    expect(wrapper.get("[data-testid=confirm-question]").text()).toBe("Delete Sitecie?")
    const go = wrapper.get("[data-testid=confirm-go]")
    expect(go.text()).toBe("Delete")
    await go.trigger("click")

    expect(wrapper.emitted("confirm")).toHaveLength(1)
  })

  it("says it is working while the removal runs", async () => {
    const wrapper = mount(ConfirmDialog, {props: {open: true, title: "Remove", question: "Remove it?", working: true}, global: {stubs: {ModalDialog}}})

    expect(wrapper.get("[data-testid=confirm-go]").text()).toBe("Removing")
    expect(wrapper.get("[data-testid=confirm-go]").attributes("disabled")).toBeDefined()
  })
})
