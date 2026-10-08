import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import RemoveSignUpDialog from "@/components/common/modals/RemoveSignUpDialog.vue"

// The island's dialog portals to the body; a stand-in keeps what it holds where it can be read.
const ModalDialog = {
  name: "ModalDialog",
  props: ["open", "title", "testid", "cancelTestid", "danger"],
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

  it("hands the dialog its Cancel and danger line, and passes a close from the dialog straight through", () => {
    const wrapper = dialog()
    const modal = wrapper.getComponent({name: "ModalDialog"})

    modal.vm.$emit("update:open", false)

    expect(wrapper.emitted("update:modelValue")).toEqual([[false]])
    expect(modal.props("testid")).toBe("remove-signup-dialog")
    expect(modal.props("cancelTestid")).toBe("remove-signup-cancel-btn")
    expect(modal.props("danger")).not.toBeUndefined()
  })
})
