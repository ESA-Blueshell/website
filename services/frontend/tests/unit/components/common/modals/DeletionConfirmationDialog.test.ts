import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import DeletionConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"

describe("DeletionConfirmationDialog", () => {
  it("emits confirm when delete action is triggered", () => {
    const wrapper = mount(DeletionConfirmationDialog, {
      props: {
        modelValue: true,
        title: "Delete",
        message: "Sure?",
      },
    })

    ;(wrapper.vm as any).confirm()
    expect(wrapper.emitted("confirm")?.length).toBe(1)
  })
})
