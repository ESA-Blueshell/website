import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import AnnounceDialog from "@/domains/events/island/AnnounceDialog.vue"

const ModalDialog = {name: "ModalDialog", props: ["open", "title", "testid"], emits: ["update:open"], template: "<div><slot /></div>"}

describe("AnnounceDialog", () => {
  it("offers now and the later morning, preselects nothing, and answers null on cancel or close", async () => {
    const wrapper = mount(AnnounceDialog, {props: {open: true, later: "Post tomorrow at 08:00"}, global: {stubs: {ModalDialog}}})

    expect(wrapper.get("[data-testid=announce-later]").text()).toBe("Post tomorrow at 08:00")
    await wrapper.get("[data-testid=announce-now]").trigger("click")
    await wrapper.get("[data-testid=announce-later]").trigger("click")
    await wrapper.get("[data-testid=announce-cancel]").trigger("click")
    wrapper.getComponent(ModalDialog).vm.$emit("update:open", false)

    expect(wrapper.emitted("answer")).toEqual([["NOW"], ["NEXT_MORNING"], [null], [null]])
  })
})
