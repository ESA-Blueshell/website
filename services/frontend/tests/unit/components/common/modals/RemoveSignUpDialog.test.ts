import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import RemoveSignUpDialog from "@/components/common/modals/RemoveSignUpDialog.vue"

// BaseModal is stubbed away by shallowMount, and with it the slot the sentence is drawn in.
const baseModalStub = {
  name: "BaseModal",
  emits: ["update:modelValue", "save"],
  template: "<div><slot /></div>",
}

describe("RemoveSignUpDialog", () => {
  it("starts with the notify box unticked and confirms silently", async () => {
    const wrapper = shallowMount(RemoveSignUpDialog, {props: {modelValue: true, personName: "Ada"}})

    expect((wrapper.vm as any).notify).toBe(false)

    await wrapper.findComponent({name: "BaseModal"}).vm.$emit("save")

    expect(wrapper.emitted("confirm")).toEqual([[false]])
  })

  it("confirms with the notify choice the board made", async () => {
    const wrapper = shallowMount(RemoveSignUpDialog, {props: {modelValue: true, personName: "Ada"}})
    ;(wrapper.vm as any).notify = true

    await wrapper.findComponent({name: "BaseModal"}).vm.$emit("save")

    expect(wrapper.emitted("confirm")).toEqual([[true]])
  })

  it("forgets the tick when it opens again", async () => {
    const wrapper = shallowMount(RemoveSignUpDialog, {props: {modelValue: false}})
    ;(wrapper.vm as any).notify = true

    await wrapper.setProps({modelValue: true})

    expect((wrapper.vm as any).notify).toBe(false)
  })

  it("names the person it is about", () => {
    const wrapper = shallowMount(RemoveSignUpDialog, {
      props: {modelValue: true, personName: "Ada Lovelace"},
      global: {stubs: {BaseModal: baseModalStub}},
    })

    expect(wrapper.text()).toContain("Remove Ada Lovelace from the sign-ups?")
  })

  it("falls back to naming the sign-up when it has no name to use", () => {
    const wrapper = shallowMount(RemoveSignUpDialog, {
      props: {modelValue: true},
      global: {stubs: {BaseModal: baseModalStub}},
    })

    expect(wrapper.text()).toContain("Remove this sign-up from the sign-ups?")
  })

  it("passes a close from the modal straight through", async () => {
    const wrapper = shallowMount(RemoveSignUpDialog, {props: {modelValue: true}})

    await wrapper.findComponent({name: "BaseModal"}).vm.$emit("update:modelValue", false)

    expect(wrapper.emitted("update:modelValue")).toEqual([[false]])
  })

  it("carries the tick the checkbox reports into the confirmation", async () => {
    const checkboxStub = {
      name: "VCheckbox",
      props: ["modelValue"],
      emits: ["update:modelValue"],
      template: "<button @click=\"$emit('update:modelValue', true)\" />",
    }
    const wrapper = shallowMount(RemoveSignUpDialog, {
      props: {modelValue: true, personName: "Ada"},
      global: {stubs: {BaseModal: baseModalStub, VCheckbox: checkboxStub}},
    })

    await wrapper.findComponent({name: "VCheckbox"}).trigger("click")
    await wrapper.findComponent({name: "BaseModal"}).vm.$emit("save")

    expect(wrapper.emitted("confirm")).toEqual([[true]])
  })
})
