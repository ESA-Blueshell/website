import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import ReplyToPicker from "@/domains/mail/island/ReplyToPicker.vue"
import {refusable} from "@/domains/mail/refusals"

const options = (wrapper: ReturnType<typeof mount>) => wrapper.getComponent({name: "SearchPicker"}).props("options") as Array<{key: string; note?: string}>

describe("where replies go", () => {
  it("offers the addresses the api lists, and any address typed in", async () => {
    const wrapper = mount(ReplyToPicker, {props: {offered: ["board@esa-blueshell.nl"], modelValue: null, testidPrefix: "reply"}})
    const picker = wrapper.getComponent({name: "SearchPicker"})
    expect(options(wrapper).map((one) => one.key)).toEqual(["board@esa-blueshell.nl"])

    picker.vm.$emit("search", " events@esa-blueshell.nl ")
    await wrapper.vm.$nextTick()
    expect(options(wrapper).at(-1)).toEqual({key: "events@esa-blueshell.nl", label: "events@esa-blueshell.nl", note: "Use this address"})
    // An address already on the list is not offered twice.
    picker.vm.$emit("search", "BOARD@esa-blueshell.nl")
    await wrapper.vm.$nextTick()
    expect(options(wrapper)).toHaveLength(1)

    picker.vm.$emit("pick", "events@esa-blueshell.nl")
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["events@esa-blueshell.nl"])
    await wrapper.setProps({modelValue: "events@esa-blueshell.nl"})
    // The typed address stays on offer once chosen.
    expect(options(wrapper).map((one) => one.key)).toEqual(["board@esa-blueshell.nl", "events@esa-blueshell.nl"])
  })

  it("says plainly when the api refuses what was typed", async () => {
    const answered = await refusable(Promise.resolve({error: {code: "ReplyToNotAnAddress", replyTo: "board"}, data: undefined}) as Parameters<typeof refusable>[0], "fallback")
    expect(answered).toEqual({ok: false, reason: "board is not an email address. Type one address for replies, such as board@esa-blueshell.nl."})
  })
})
