import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import DeleteCommitteeDialog from "@/domains/committees/island/DeleteCommitteeDialog.vue"

const adapter = vi.hoisted(() => ({
  listCommittees: vi.fn(),
  readEventsToHandOver: vi.fn(),
  removeCommittee: vi.fn(),
  setCommitteeArchived: vi.fn(),
}))
vi.mock("@/domains/committees/adapters/committees", () => adapter)

const ModalDialog = {
  name: "ModalDialog",
  props: ["open", "title", "testid", "cancelTestid", "danger"],
  emits: ["update:open"],
  template: "<div v-if='open' :data-testid='testid'><slot /><slot name='footer' /></div>",
}
const stubs = {ModalDialog}

const lan = {id: 1, name: "LanCie", archived: false}
const committees = [
  {...lan},
  {id: 2, name: "Activitiescie", archived: false},
  {id: 3, name: "Oldcie", archived: true},
]

const mounted = async (count: number | null, committee = lan) => {
  adapter.readEventsToHandOver.mockResolvedValue(count)
  const wrapper = mount(DeleteCommitteeDialog, {props: {open: false, committee}, global: {stubs}})
  await wrapper.setProps({open: true})
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  Object.values(adapter).forEach((one) => one.mockReset())
  adapter.listCommittees.mockResolvedValue(committees)
  adapter.removeCommittee.mockResolvedValue({ok: true})
})

describe("deleting a committee", () => {
  it("says archiving is the usual way, and deletes one without events naming no taker", async () => {
    const wrapper = await mounted(0)

    expect(wrapper.text()).toContain("A committee is normally archived")
    expect(wrapper.get('[data-testid="committee-remove-what"]').text()).toContain("It organises no events.")
    expect(wrapper.get('[data-testid="committee-remove-what"]').text()).toContain("Its Discord role and Brevo list stay as they are")
    expect(wrapper.find('[data-testid="committee-remove-taker"]').exists()).toBe(false)
    await wrapper.get('[data-testid="committee-remove-confirm"]').trigger("click")
    await flushPromises()

    expect(adapter.removeCommittee).toHaveBeenCalledWith(1, undefined)
    expect(wrapper.emitted("removed")).toHaveLength(1)
    expect(wrapper.emitted("update:open")).toEqual([[false]])
  })

  it("names how many events move and to whom, offers only committees that may take them, and says why a deletion was refused", async () => {
    const wrapper = await mounted(3)
    const confirm = () => wrapper.get('[data-testid="committee-remove-confirm"]')

    expect(wrapper.get('[data-testid="committee-remove-what"]').text()).toContain("Its 3 events move to the committee you pick, past ones included")
    expect(confirm().attributes("disabled")).toBeDefined()
    const picker = wrapper.getComponent({name: "SearchPicker"})
    expect((picker.props("options") as Array<{key: string}>).map((one) => one.key)).toEqual(["2"])
    picker.vm.$emit("pick", "2")
    await flushPromises()
    expect(wrapper.get('[data-testid="committee-remove-what"]').text()).toContain("move to Activitiescie")

    adapter.removeCommittee.mockResolvedValueOnce({ok: false, reason: "Activitiescie is archived, so it cannot take over events."})
    await confirm().trigger("click")
    await flushPromises()
    expect(wrapper.get('[data-testid="committee-remove-failure"]').text()).toBe("Activitiescie is archived, so it cannot take over events.")
    expect(wrapper.emitted("update:open")).toBeUndefined()

    await confirm().trigger("click")
    await flushPromises()
    expect(adapter.removeCommittee).toHaveBeenLastCalledWith(1, 2)
    expect(wrapper.emitted("removed")).toHaveLength(1)
  })

  it("archives it the way the page's Archive does, and says why archiving was refused", async () => {
    const wrapper = await mounted(1)
    expect(wrapper.get('[data-testid="committee-remove-what"]').text()).toContain("Its 1 event moves")

    adapter.setCommitteeArchived.mockResolvedValueOnce({ok: false, reason: "The committee could not be archived."})
    await wrapper.get('[data-testid="committee-remove-archive"]').trigger("click")
    await flushPromises()
    expect(wrapper.get('[data-testid="committee-remove-failure"]').text()).toBe("The committee could not be archived.")

    adapter.setCommitteeArchived.mockResolvedValueOnce({ok: true, saved: {...lan, archived: true}})
    await wrapper.get('[data-testid="committee-remove-archive"]').trigger("click")
    await flushPromises()
    expect(adapter.setCommitteeArchived).toHaveBeenLastCalledWith(1, true)
    expect(wrapper.emitted("archived")).toEqual([[{...lan, archived: true}]])
    expect(adapter.removeCommittee).not.toHaveBeenCalled()
    wrapper.getComponent(ModalDialog).vm.$emit("update:open", false)
    expect(wrapper.emitted("update:open")).toEqual([[false], [false]])
  })

  it("offers no Archive for an archived committee, and holds Delete when the events cannot be counted", async () => {
    adapter.listCommittees.mockRejectedValue(new Error("down"))
    const wrapper = await mounted(null, {...lan, archived: true})

    expect(wrapper.find('[data-testid="committee-remove-archive"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="committee-remove-failure"]').text()).toContain("could not be read")
    expect(wrapper.get('[data-testid="committee-remove-confirm"]').attributes("disabled")).toBeDefined()
  })
})
