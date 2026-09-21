import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import RemoveSignUpDialog from "@/components/common/modals/RemoveSignUpDialog.vue"

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
})
