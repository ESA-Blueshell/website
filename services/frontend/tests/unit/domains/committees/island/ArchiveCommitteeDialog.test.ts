import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import ArchiveCommitteeDialog from "@/domains/committees/island/ArchiveCommitteeDialog.vue"

const adapter = vi.hoisted(() => ({setCommitteeArchived: vi.fn()}))
vi.mock("@/domains/committees/adapters/committees", () => adapter)

const ConfirmDialog = {name: "ConfirmDialog", props: ["open", "title", "question", "confirmLabel", "workingLabel", "failure", "working", "testid"], emits: ["confirm", "update:open"], template: "<div />"}

const lan = {id: 1, name: "LanCie", archived: false}

beforeEach(() => adapter.setCommitteeArchived.mockReset())

describe("archiving a committee", () => {
  it("says what archiving does, and does it once confirmed", async () => {
    adapter.setCommitteeArchived.mockResolvedValue({ok: true, committee: {...lan, archived: true}})
    const wrapper = mount(ArchiveCommitteeDialog, {props: {open: true, committee: lan}, global: {stubs: {ConfirmDialog}}})
    const confirm = wrapper.getComponent(ConfirmDialog)

    expect(confirm.props("title")).toBe("Archive LanCie?")
    expect(confirm.props("question")).toContain("joins the committees we used to have")
    confirm.vm.$emit("confirm")
    confirm.vm.$emit("confirm")
    await flushPromises()

    expect(adapter.setCommitteeArchived).toHaveBeenCalledTimes(1)
    expect(adapter.setCommitteeArchived).toHaveBeenCalledWith(1, true)
    expect(wrapper.emitted("saved")).toEqual([[{...lan, archived: true}]])
    expect(wrapper.emitted("update:open")).toEqual([[false]])
  })

  it("brings an archived committee back, and says why when it could not", async () => {
    adapter.setCommitteeArchived.mockResolvedValue({ok: false, reason: "The committee could not be brought back."})
    const wrapper = mount(ArchiveCommitteeDialog, {props: {open: false, committee: {...lan, archived: true}}, global: {stubs: {ConfirmDialog}}})
    await wrapper.setProps({open: true})
    const confirm = wrapper.getComponent(ConfirmDialog)

    expect(confirm.props("confirmLabel")).toBe("Bring it back")
    expect(confirm.props("question")).toContain("goes back on the reel")
    confirm.vm.$emit("confirm")
    await flushPromises()

    expect(adapter.setCommitteeArchived).toHaveBeenCalledWith(1, false)
    expect(confirm.props("failure")).toBe("The committee could not be brought back.")
    confirm.vm.$emit("update:open", false)
    expect(wrapper.emitted("update:open")).toEqual([[false]])
  })
})
