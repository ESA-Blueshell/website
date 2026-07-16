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

  it("renders SubmitButton (rich save) when saveIcon is provided", () => {
    const wrapper = mountModal({
      showSave: true,
      saveTestid: "my-rich-save",
      saveIcon: "mdi-content-save",
    })
    // The SubmitButton wraps a v-btn — testid should be on it
    expect(wrapper.find("[data-testid='my-rich-save']").exists()).toBe(true)
  })

  it("renders SubmitButton when saveShowStatus=true", () => {
    const wrapper = mountModal({
      showSave: true,
      saveTestid: "my-status-save",
      saveShowStatus: true,
    })
    expect(wrapper.find("[data-testid='my-status-save']").exists()).toBe(true)
  })

  it("emits save when SubmitButton is clicked", async () => {
    const wrapper = mountModal({
      showSave: true,
      saveTestid: "my-rich-save",
      saveIcon: "mdi-content-save",
    })
    await wrapper.find("[data-testid='my-rich-save']").trigger("click")
    expect(wrapper.emitted("save")).toBeTruthy()
  })

  it("renders custom #save slot content when provided", () => {
    const wrapper = mount(BaseModal, {
      props: {modelValue: true, title: "Custom", showSave: true},
      slots: {
        default: "<p>body</p>",
        save: "<button data-testid=\"custom-save-btn\">Custom Save</button>",
      },
    })
    expect(wrapper.find("[data-testid='custom-save-btn']").exists()).toBe(true)
  })

  it("renders custom #actions slot as full footer override", () => {
    const wrapper = mount(BaseModal, {
      props: {modelValue: true, title: "Actions", showCancel: true},
      slots: {
        default: "<p>body</p>",
        actions: "<button data-testid=\"custom-action-btn\">Do It</button>",
      },
    })
    expect(wrapper.find("[data-testid='custom-action-btn']").exists()).toBe(true)
    // Cancel button should NOT appear since the #actions slot fully overrides it
    expect(wrapper.find("[data-testid='base-modal-cancel']").exists()).toBe(false)
  })
})
