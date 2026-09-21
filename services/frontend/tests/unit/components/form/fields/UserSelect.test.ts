import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import UserSelect from "@/components/form/fields/UserSelect.vue"

const {mockSearch} = vi.hoisted(() => ({mockSearch: vi.fn()}))
vi.mock("@/domains/user", () => ({searchMemberAccounts: mockSearch}))

const alice = {id: 7, fullName: "Alice", email: "alice@example.com", roles: ["MEMBER"]}
const zoe = {id: 5410, fullName: "Zoe", email: "zoe@example.com", roles: ["MEMBER"]}

/** The field settles for 250ms before it asks, so a test has to let that pass. */
async function settleSearch(wrapper: {vm: {$nextTick: () => Promise<void>}}) {
  await vi.advanceTimersByTimeAsync(300)
  await wrapper.vm.$nextTick()
}

/** The field's own chrome is not what these tests are about, so it is passed through. */
const throughField = {template: "<div><slot /></div>"}

function mountSelect(users: unknown[], modelValue?: number) {
  return shallowMount(UserSelect, {
    props: {users, modelValue},
    global: {stubs: {IslandField: throughField}},
  })
}

const picker = (wrapper: ReturnType<typeof mountSelect>) =>
  wrapper.findComponent({name: "IslandPicker"})

function selected(wrapper: ReturnType<typeof mountSelect>) {
  return picker(wrapper).props("selectedKey")
}

function offered(wrapper: ReturnType<typeof mountSelect>) {
  return picker(wrapper).props("options") as {key: string}[]
}

async function type(wrapper: ReturnType<typeof mountSelect>, term: string) {
  picker(wrapper).vm.$emit("search", term)
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

    expect(selected(wrapper)).toBe(String(alice.id))
  })

  it("shows the picked user when the list is already there", () => {
    expect(selected(mountSelect([alice], alice.id))).toBe(String(alice.id))
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
    expect(offered(wrapper)).toContainEqual(expect.objectContaining({key: String(zoe.id)}))
  })

  it("keeps the picked user in the list when the answer does not name them", async () => {
    const wrapper = mountSelect([alice], alice.id)

    await type(wrapper, "Zoe")

    expect(offered(wrapper)).toContainEqual(expect.objectContaining({key: String(alice.id)}))
    expect(selected(wrapper)).toBe(String(alice.id))
  })

  it("asks once for a name typed a letter at a time", async () => {
    const wrapper = mountSelect([])

    picker(wrapper).vm.$emit("search", "Z")
    picker(wrapper).vm.$emit("search", "Zo")
    await type(wrapper, "Zoe")

    expect(mockSearch).toHaveBeenCalledTimes(1)
    expect(mockSearch).toHaveBeenCalledWith("Zoe", 20)
  })

  it("leaves the searching to the api rather than filtering the page again", () => {
    // A row the api found by a phone number carries no phone number in what it draws, so a
    // second filter here would hide exactly the answer that was asked for.
    expect(picker(mountSelect([alice])).props("remote")).toBe(true)
  })

  it("offers a person by their handle, their address and their number", () => {
    const wrapper = mountSelect([{...alice, discord: "alice#1234", phoneNumber: "+31612345678",
      firstName: "Alice", lastName: "Arens", initials: "A.", username: "alice"}])

    expect(offered(wrapper)[0].terms).toEqual(expect.arrayContaining([
      "alice#1234", "+31612345678", "alice@example.com", "Arens",
    ]))
  })
  it("resolves a later pick against the form's list once a search has replaced the options", async () => {
    const wrapper = mountSelect([alice])

    await type(wrapper, "zo")
    expect(offered(wrapper).map((u) => u.id)).toEqual([5410])

    await wrapper.setProps({modelValue: 7})

    expect(selected(wrapper)).toMatchObject({id: 7})
  })
})

describe("a member the list cannot account for yet", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mockSearch.mockReset()
    mockSearch.mockResolvedValue([zoe])
  })

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

    expect(selected(wrapper)).toBe(String(alice.id))
    expect((wrapper.emitted("update:modelValue") ?? []).filter(([id]) => id === undefined)).toHaveLength(0)
  })
})
