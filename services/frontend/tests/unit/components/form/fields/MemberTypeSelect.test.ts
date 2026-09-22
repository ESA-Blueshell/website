import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import MemberTypeSelect from "@/components/form/fields/MemberTypeSelect.vue"
import {MemberType} from "@/domains/user"

describe("MemberTypeSelect", () => {
  it("offers every member type the api names, each written as a word", () => {
    const wrapper = shallowMount(MemberTypeSelect, {props: {modelValue: MemberType.REGULAR}})

    const options = (wrapper.vm as any).memberTypeOptions
    expect(options.map((one: {value: string}) => one.value)).toEqual(Object.values(MemberType))
    expect(options.map((one: {text: string}) => one.text)).toContain("Regular")
  })

  it("passes a pick up to whatever holds the form", async () => {
    const wrapper = shallowMount(MemberTypeSelect, {props: {modelValue: MemberType.ALUMNI}})

    ;(wrapper.vm as any).selected = MemberType.REGULAR
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([MemberType.REGULAR])
  })

  it("follows the form when the form changes the type under it", async () => {
    const wrapper = shallowMount(MemberTypeSelect, {props: {modelValue: MemberType.ALUMNI}})

    await wrapper.setProps({modelValue: MemberType.REGULAR})

    expect((wrapper.vm as any).selected).toBe(MemberType.REGULAR)
  })

  // The same value arriving again is the form echoing this field's own pick back at it.
  it("stays where it is when the form echoes the type it already holds", async () => {
    const wrapper = shallowMount(MemberTypeSelect, {props: {modelValue: MemberType.ALUMNI}})

    ;(wrapper.vm as any).selected = MemberType.REGULAR
    await wrapper.vm.$nextTick()
    await wrapper.setProps({modelValue: MemberType.REGULAR})

    expect(wrapper.emitted("update:modelValue")).toHaveLength(1)
  })

  it("holds what the reader picked in the select itself", async () => {
    const wrapper = shallowMount(MemberTypeSelect, {props: {modelValue: MemberType.ALUMNI}})

    wrapper.findComponent({name: "VSelect"}).vm.$emit("update:modelValue", MemberType.REGULAR)
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([MemberType.REGULAR])
  })

  it("refuses a form with no member type on it", () => {
    const wrapper = shallowMount(MemberTypeSelect, {props: {modelValue: MemberType.ALUMNI}})

    const rule = (wrapper.vm as any).requiredRule
    expect(rule(undefined)).toBe("Member type is required")
    expect(rule(MemberType.REGULAR)).toBe(true)
  })
})
