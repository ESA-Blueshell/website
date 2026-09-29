import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import ArchiveDialog from "@/components/island/ArchiveDialog.vue"

const ConfirmDialog = {name: "ConfirmDialog", props: ["open", "title", "question", "confirmLabel", "workingLabel", "failure", "working", "testid"], emits: ["confirm", "update:open"], template: "<div />"}

const dialog = (save: (archived: boolean) => Promise<{ok: true; saved: string} | {ok: false; reason: string}>) =>
  mount(ArchiveDialog, {
    props: {open: true, name: "Chess", archived: false, leaving: "It leaves.", returning: "It returns.", save, testid: "archive"},
    global: {stubs: {ConfirmDialog}},
  })

describe("ArchiveDialog", () => {
  it("keeps a refusal while it closes, and forgets it once it opens again", async () => {
    const wrapper = dialog(vi.fn().mockResolvedValue({ok: false, reason: "Not now."}))
    const confirm = wrapper.getComponent(ConfirmDialog)

    confirm.vm.$emit("confirm")
    await flushPromises()
    await wrapper.setProps({open: false})
    expect(confirm.props("failure")).toBe("Not now.")

    await wrapper.setProps({open: true})
    expect(confirm.props("failure")).toBeNull()
  })
})
