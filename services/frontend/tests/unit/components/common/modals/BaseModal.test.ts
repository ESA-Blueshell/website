import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import BaseModal from "@/components/common/modals/BaseModal.vue"

function mountModal(props: Record<string, unknown> = {}) {
  return mount(BaseModal, {
    props: {
      modelValue: true,
      title: "Test Title",
      ...props,
    },
    slots: {
      default: "<p>Dialog body</p>",
    },
  })
}

describe("BaseModal", () => {
  it("renders the title", () => {
    const wrapper = mountModal()
    expect(wrapper.text()).toContain("Test Title")
  })

  it("renders slot content", () => {
    const wrapper = mountModal()
    expect(wrapper.text()).toContain("Dialog body")
  })

  it("does NOT render Save button when showSave is false (default)", () => {
    const wrapper = mountModal()
    expect(wrapper.find("[data-testid='my-save']").exists()).toBe(false)
  })

  it("renders Save button when showSave=true", () => {
    const wrapper = mountModal({showSave: true, saveTestid: "my-save"})
    expect(wrapper.find("[data-testid='my-save']").exists()).toBe(true)
  })

  it("emits save when Save button clicked", async () => {
    const wrapper = mountModal({showSave: true, saveTestid: "my-save"})
    await wrapper.find("[data-testid='my-save']").trigger("click")
    expect(wrapper.emitted("save")).toBeTruthy()
  })

  it("does NOT render Delete button when showDelete is false (default)", () => {
    const wrapper = mountModal()
    expect(wrapper.find("[data-testid='my-delete']").exists()).toBe(false)
  })

  it("renders Delete button when showDelete=true", () => {
    const wrapper = mountModal({showDelete: true, deleteTestid: "my-delete"})
    expect(wrapper.find("[data-testid='my-delete']").exists()).toBe(true)
  })

  it("emits delete when Delete button clicked", async () => {
    const wrapper = mountModal({showDelete: true, deleteTestid: "my-delete"})
    await wrapper.find("[data-testid='my-delete']").trigger("click")
    expect(wrapper.emitted("delete")).toBeTruthy()
  })

  it("renders Cancel button by default", () => {
    const wrapper = mountModal({cancelTestid: "my-cancel"})
    expect(wrapper.find("[data-testid='my-cancel']").exists()).toBe(true)
  })

  it("does NOT render Cancel button when showCancel=false", () => {
    const wrapper = mountModal({showCancel: false, cancelTestid: "my-cancel"})
    expect(wrapper.find("[data-testid='my-cancel']").exists()).toBe(false)
  })

  it("Cancel button emits update:modelValue=false and cancel", async () => {
    const wrapper = mountModal({cancelTestid: "my-cancel"})
    await wrapper.find("[data-testid='my-cancel']").trigger("click")
    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual([false])
    expect(wrapper.emitted("cancel")).toBeTruthy()
  })

  it("honours testid prop on dialog", () => {
    const wrapper = mountModal({testid: "custom-modal-id"})
    expect(wrapper.find("[data-testid='custom-modal-id']").exists()).toBe(true)
  })

  it("falls back to data-testid=base-modal when testid not given", () => {
    const wrapper = mountModal()
    expect(wrapper.find("[data-testid='base-modal']").exists()).toBe(true)
  })
})
