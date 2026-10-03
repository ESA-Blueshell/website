import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import BrevoListFields from "@/domains/cohorts/island/BrevoListFields.vue"
import type {BrevoPlace} from "@/services/api"

const api = vi.hoisted(() => ({findTargetOverview: vi.fn()}))
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const throughField = {props: ["label", "testid"], template: "<div :data-testid='testid'><slot :control-id=\"'c'\" :label-id=\"'l'\" /></div>"}
const section = {props: ["title"], template: "<section><slot /></section>"}
const overview = {lists: [
  {externalId: "7", label: "Sitecie", targetId: 1, enforced: false},
  {externalId: "8", label: "Old Pub Quiz", folderLabel: "Committees", enforced: false},
  {externalId: "9", label: "LAN 2019", folderLabel: "Archive", enforced: false},
], missing: []}

const mountFields = async (read: (() => Promise<BrevoPlace | null>) | null) => {
  const wrapper = mount(BrevoListFields, {
    props: {read, name: "Pub Quiz Cie"},
    global: {stubs: {
      FormField: throughField,
      FormSection: section,
      SearchPicker: {name: "SearchPicker", props: ["options", "selectedKey", "testidPrefix"], emits: ["pick"], template: "<div />"},
    }},
  })
  await flushPromises()
  return wrapper
}

describe("a committee's Brevo list on its form", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.findTargetOverview.mockResolvedValue({status: 200, data: overview})
  })

  it("asks a new committee for a new list by default, or a free list in Brevo", async () => {
    const wrapper = await mountFields(null)
    const picker = wrapper.findComponent({name: "SearchPicker"})

    expect(picker.props("options").map((one: {key: string}) => one.key)).toEqual(["__new__", "8"])
    expect(picker.props("options")[0].label).toBe("A new list, Pub Quiz Cie")
    expect(wrapper.emitted("update:modelValue")!.at(-1)).toEqual([{listId: null, createList: true}])

    picker.vm.$emit("pick", "8")
    await flushPromises()
    expect(wrapper.emitted("update:modelValue")!.at(-1)).toEqual([{listId: "8", createList: false}])
  })

  it("shows a linked list and asks nothing of it, and hides where Brevo cannot be asked", async () => {
    const linked = await mountFields(() => Promise.resolve({available: true, listId: "7", listName: "Sitecie", folder: "Committees"}))
    expect(linked.get('[data-testid="committee-edit-brevo-list"]').text()).toContain("Sitecie")
    expect(linked.text()).toContain("In the Committees folder.")
    expect(linked.emitted("update:modelValue")).toBeUndefined()

    const away = await mountFields(() => Promise.resolve({available: false, listId: null, listName: null, folder: null}))
    expect(away.find('[data-testid="committee-edit-brevo"]').exists()).toBe(false)

    api.findTargetOverview.mockResolvedValue({status: 503, error: null})
    expect((await mountFields(null)).find('[data-testid="committee-edit-brevo"]').exists()).toBe(false)
  })
})
