import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import UserSelect from "@/components/form/fields/UserSelect.vue"

const {mockSearch} = vi.hoisted(() => ({mockSearch: vi.fn()}))
vi.mock("@/domains/user", () => ({searchMemberAccounts: mockSearch}))

const alice = {id: 7, fullName: "Alice", roles: ["MEMBER"]}
const zoe = {id: 5410, fullName: "Zoe", roles: ["MEMBER"]}

/** The field settles for 250ms before it asks, so a test has to let that pass. */
async function settleSearch(wrapper: {vm: {$nextTick: () => Promise<void>}}) {
  await vi.advanceTimersByTimeAsync(300)
  await wrapper.vm.$nextTick()
}

function mountSelect(users: unknown[], modelValue?: number) {
  return shallowMount(UserSelect, {props: {users, modelValue}})
}

function selected(wrapper: ReturnType<typeof mountSelect>) {
  return wrapper.findComponent({name: "VAutocomplete"}).props("modelValue")
}

function offered(wrapper: ReturnType<typeof mountSelect>) {
  return wrapper.findComponent({name: "VAutocomplete"}).props("items") as {id: number}[]
}

async function type(wrapper: ReturnType<typeof mountSelect>, term: string) {
  await wrapper.findComponent({name: "VAutocomplete"}).vm.$emit("update:search", term)
  await settleSearch(wrapper)
}

describe("UserSelect", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mockSearch.mockReset()
    mockSearch.mockResolvedValue([zoe])
  })

  it("shows the picked user once the list arrives after mount", async () => {
    const wrapper = mountSelect([], alice.id)
    expect(selected(wrapper)).toBeFalsy()

    await wrapper.setProps({users: [alice]})

    expect(selected(wrapper)).toMatchObject({id: alice.id, fullName: "Alice"})
  })

  it("shows the picked user when the list is already there", () => {
    expect(selected(mountSelect([alice], alice.id))).toMatchObject({id: alice.id})
  })

  it("stays empty when nothing was picked", async () => {
    const wrapper = mountSelect([], undefined)

    await wrapper.setProps({users: [alice]})

    expect(selected(wrapper)).toBeFalsy()
  })

  it("asks the api for what was typed rather than filtering what it already holds", async () => {
    // The table is larger than any list the browser can hold, so a name nobody prefetched
    // has to be reachable — this is the whole of #1139.
    const wrapper = mountSelect([alice])

    await type(wrapper, "Zoe")

    expect(mockSearch).toHaveBeenCalledWith("Zoe", 20)
    expect(offered(wrapper)).toContainEqual(expect.objectContaining({id: zoe.id}))
  })

  it("keeps the picked user in the list when the answer does not name them", async () => {
    const wrapper = mountSelect([alice], alice.id)

    await type(wrapper, "Zoe")

    expect(offered(wrapper)).toContainEqual(expect.objectContaining({id: alice.id}))
    expect(selected(wrapper)).toMatchObject({id: alice.id})
  })

  it("asks once for a name typed a letter at a time", async () => {
    const wrapper = mountSelect([])

    await wrapper.findComponent({name: "VAutocomplete"}).vm.$emit("update:search", "Z")
    await wrapper.findComponent({name: "VAutocomplete"}).vm.$emit("update:search", "Zo")
    await type(wrapper, "Zoe")

    expect(mockSearch).toHaveBeenCalledTimes(1)
    expect(mockSearch).toHaveBeenCalledWith("Zoe", 20)
  })

  it("does not ask again for the name it is already showing", async () => {
    const wrapper = mountSelect([alice], alice.id)

    await type(wrapper, "Alice")

    expect(mockSearch).not.toHaveBeenCalled()
  })
})

describe("a member the list cannot account for yet", () => {
  /**
   * The parent's value survives a list that does not contain it.
   *
   * `users` is one page of an unbounded table, fetched after this field mounts, so a member
   * off that page cannot be resolved to a user yet. Emitting `undefined` there does not blank
   * a field — it clears the id out of the form, the picked-a-user rule then refuses the save,
   * and the submit sends no request at all. Which is the whole bug, three times over.
   */
  it("does not clear an id it simply cannot resolve", async () => {
    const wrapper = mountSelect([], 4242)
    await wrapper.vm.$nextTick()

    const cleared = (wrapper.emitted("update:modelValue") ?? []).filter(([id]) => id === undefined)
    expect(cleared).toHaveLength(0)
  })

  it("resolves it once the list arrives, without having lost it", async () => {
    const wrapper = mountSelect([], alice.id)
    await wrapper.vm.$nextTick()

    await wrapper.setProps({users: [alice]})
    await wrapper.vm.$nextTick()

    expect(selected(wrapper)).toMatchObject({id: alice.id})
    expect((wrapper.emitted("update:modelValue") ?? []).filter(([id]) => id === undefined)).toHaveLength(0)
  })

  /** A field the reader can actually see is a field the reader can actually clear. */
  it("still reports a user clearing a value that was resolved", async () => {
    const wrapper = mountSelect([alice], alice.id)
    await wrapper.vm.$nextTick()

    await wrapper.findComponent({name: "VAutocomplete"}).vm.$emit("update:modelValue", undefined)
    await wrapper.vm.$nextTick()

    expect((wrapper.emitted("update:modelValue") ?? []).some(([id]) => id === undefined)).toBe(true)
  })
})
