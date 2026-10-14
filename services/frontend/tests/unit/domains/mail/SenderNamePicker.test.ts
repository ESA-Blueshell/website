import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import SenderNamePicker from "@/domains/mail/island/SenderNamePicker.vue"

const options = (wrapper: ReturnType<typeof mount>) => wrapper.getComponent({name: "SearchPicker"}).props("options") as Array<{key: string; note?: string}>

describe("the name an email goes out under", () => {
  it("offers the names the site lists, and any name typed in", async () => {
    const wrapper = mount(SenderNamePicker, {props: {offered: ["Events", "Blueshell"], modelValue: "Events", testidPrefix: "name"}})
    const picker = wrapper.getComponent({name: "SearchPicker"})
    expect(options(wrapper).map((one) => one.key)).toEqual(["Events", "Blueshell"])

    picker.vm.$emit("search", " Events team ")
    await wrapper.vm.$nextTick()
    expect(options(wrapper).at(-1)).toEqual({key: "Events team", label: "Events team", note: "Use this name"})
    // A name already on the list is not offered twice.
    picker.vm.$emit("search", "Blueshell")
    await wrapper.vm.$nextTick()
    expect(options(wrapper)).toHaveLength(2)

    picker.vm.$emit("pick", "Events team")
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["Events team"])
    await wrapper.setProps({modelValue: "Events team"})
    expect(options(wrapper).map((one) => one.key)).toEqual(["Events", "Blueshell", "Events team"])
  })
})
