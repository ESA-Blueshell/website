import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import RemoveSignUpDialog from "@/components/common/modals/RemoveSignUpDialog.vue"

// The island's dialog portals to the body; a stand-in keeps what it holds where it can be read.
const ModalDialog = {
  name: "ModalDialog",
  props: ["open", "title", "testid"],
  emits: ["update:open"],
  template: "<div><slot /><slot name='footer' /></div>",
}

const dialog = (props: Record<string, unknown> = {modelValue: true, personName: "Ada"}) =>
  mount(RemoveSignUpDialog, {props, global: {stubs: {ModalDialog}}})

describe("RemoveSignUpDialog", () => {
  it("starts with the notify box unticked and confirms silently", async () => {
    const wrapper = dialog()

    await wrapper.get("[data-testid=remove-signup-confirm-btn]").trigger("click")

    expect(wrapper.emitted("confirm")).toEqual([[false]])
  })

  it("confirms with the notify choice the board made", async () => {
    const wrapper = dialog()

    await wrapper.get("[data-testid=remove-signup-notify]").setValue(true)
    await wrapper.get("[data-testid=remove-signup-confirm-btn]").trigger("click")

    expect(wrapper.emitted("confirm")).toEqual([[true]])
  })

  it("forgets the tick when it opens again, and keeps it while it closes", async () => {
    const wrapper = dialog({modelValue: true})
    await wrapper.get("[data-testid=remove-signup-notify]").setValue(true)
    await wrapper.setProps({modelValue: false})
    expect((wrapper.get("[data-testid=remove-signup-notify]").element as HTMLInputElement).checked).toBe(true)

    await wrapper.setProps({modelValue: true})
    await wrapper.get("[data-testid=remove-signup-confirm-btn]").trigger("click")

    expect(wrapper.emitted("confirm")).toEqual([[false]])
  })

  it("names the person it is about, or the sign-up where it has no name", () => {
    expect(dialog().text()).toContain("Remove Ada from the sign-ups?")
    expect(dialog({modelValue: true}).text()).toContain("Remove this sign-up from the sign-ups?")
  })

  it("closes on Cancel, and passes a close from the dialog straight through", async () => {
    const wrapper = dialog()

    await wrapper.get("[data-testid=remove-signup-cancel-btn]").trigger("click")
    wrapper.getComponent({name: "ModalDialog"}).vm.$emit("update:open", false)

    expect(wrapper.emitted("update:modelValue")).toEqual([[false], [false]])
    expect(wrapper.getComponent({name: "ModalDialog"}).props("testid")).toBe("remove-signup-dialog")
  })
})
