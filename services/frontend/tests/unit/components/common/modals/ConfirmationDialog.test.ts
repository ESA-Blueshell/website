import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import ConfirmationDialog from "@/components/common/modals/ConfirmationDialog.vue"

function mountDialog(props: Record<string, unknown> = {}) {
  return mount(ConfirmationDialog, {
    props: {
      modelValue: true,
      title: "Confirm action",
      message: "Are you sure?",
      ...props,
    },
  })
}

describe("ConfirmationDialog", () => {
  it("renders message", () => {
    const wrapper = mountDialog()
    expect(wrapper.text()).toContain("Are you sure?")
  })

  it("renders confirm button with default testid", () => {
    const wrapper = mountDialog()
    expect(wrapper.find("[data-testid='confirmation-dialog-confirm-btn']").exists()).toBe(true)
  })

  it("renders cancel button with default testid", () => {
    const wrapper = mountDialog()
    expect(wrapper.find("[data-testid='confirmation-dialog-cancel-btn']").exists()).toBe(true)
  })

  it("emits confirm when confirm button clicked", async () => {
    const wrapper = mountDialog()
    await wrapper.find("[data-testid='confirmation-dialog-confirm-btn']").trigger("click")
    expect(wrapper.emitted("confirm")).toBeTruthy()
  })

  it("emits update:modelValue=false when cancel button clicked", async () => {
    const wrapper = mountDialog()
    await wrapper.find("[data-testid='confirmation-dialog-cancel-btn']").trigger("click")
    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual([false])
  })

  it("uses custom testid for button ids", () => {
    const wrapper = mountDialog({testid: "my-confirmation"})
    expect(wrapper.find("[data-testid='my-confirmation-confirm-btn']").exists()).toBe(true)
    expect(wrapper.find("[data-testid='my-confirmation-cancel-btn']").exists()).toBe(true)
  })

  it("uses custom confirmLabel", () => {
    const wrapper = mountDialog({confirmLabel: "Remove"})
    expect(wrapper.find("[data-testid='confirmation-dialog-confirm-btn']").text()).toContain("Remove")
  })

  it("dialog has data-testid matching testid prop", () => {
    const wrapper = mountDialog({testid: "custom-dialog"})
    expect(wrapper.find("[data-testid='custom-dialog']").exists()).toBe(true)
  })
})
