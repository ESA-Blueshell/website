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
  it("resolves a later pick against the form's list once a search has replaced the options", async () => {
    const wrapper = mountSelect([alice])

    await type(wrapper, "zo")
    expect(offered(wrapper).map((u) => u.id)).toEqual([5410])

    await wrapper.setProps({modelValue: 7})

    expect(selected(wrapper)).toMatchObject({id: 7})
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

  it("passes the pick up by its number, and nothing once it is cleared", async () => {
    const wrapper = mountSelect([alice], 7)

    wrapper.findComponent({name: "VAutocomplete"}).vm.$emit("update:modelValue", zoe)
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([5410])

    await wrapper.setProps({modelValue: undefined})
    expect(selected(wrapper)).toBeFalsy()
  })

  // The name of the picked member arrives here as a search the reader did not type.
  it("does not ask again for the name it is already showing", async () => {
    const wrapper = mountSelect([alice], 7)

    await type(wrapper, "Alice")

    expect(mockSearch).not.toHaveBeenCalled()
  })

  it("names a member by their discord handle where there is one", () => {
    const wrapper = mountSelect([{...alice, discord: "alice#1"}], 7)

    expect((wrapper.vm as any).itemTitle({...alice, discord: "alice#1"})).toBe("Alice (alice#1)")
    expect((wrapper.vm as any).itemTitle(alice)).toBe("Alice")
  })

  it("hands validation, resetting and focus to the input it draws", () => {
    const wrapper = mountSelect([alice], 7)
    const input = {validate: vi.fn(), resetValidation: vi.fn(), focus: vi.fn()}
    ;(wrapper.vm as any).inputRef = input

    ;(wrapper.vm as any).validate()
    ;(wrapper.vm as any).resetValidation()
    ;(wrapper.vm as any).focus()

    expect(input.validate).toHaveBeenCalled()
    expect(input.resetValidation).toHaveBeenCalled()
    expect(input.focus).toHaveBeenCalled()
  })

  it("asks nothing of an input that is not there", () => {
    const wrapper = mountSelect([alice], 7)
    ;(wrapper.vm as any).inputRef = null

    expect(() => {
      ;(wrapper.vm as any).validate()
      ;(wrapper.vm as any).resetValidation()
      ;(wrapper.vm as any).focus()
    }).not.toThrow()
  })

  it("stops a settling search when the field goes away", async () => {
    const wrapper = mountSelect([alice])

    await wrapper.findComponent({name: "VAutocomplete"}).vm.$emit("update:search", "zo")
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(300)

    expect(mockSearch).not.toHaveBeenCalled()
  })

  it("refuses an empty field when the form named no rules of its own", () => {
    const wrapper = mountSelect([alice])

    const rule = (wrapper.findComponent({name: "VAutocomplete"}).props("rules") as Array<
      (v: unknown) => true | string
    >)[0]!

    expect(rule(undefined)).toBe("Select a user")
    expect(rule(alice)).toBe(true)
  })

  it("asks nothing when the field is cleared rather than typed in", async () => {
    const wrapper = mountSelect([alice])

    await wrapper.findComponent({name: "VAutocomplete"}).vm.$emit("update:search", undefined)
    await settleSearch(wrapper)

    expect(mockSearch).not.toHaveBeenCalled()
  })

  // The reader typed again before the first answer came back, so the first answer is stale.
  it("lets the newer search win, whichever order the answers arrive in", async () => {
    let releaseFirst: (value: unknown[]) => void = () => undefined
    mockSearch.mockImplementationOnce(() => new Promise((resolve) => {
      releaseFirst = resolve
    }))
    mockSearch.mockResolvedValueOnce([zoe])
    const wrapper = mountSelect([])

    await type(wrapper, "zo")
    await type(wrapper, "zoe")
    releaseFirst([alice])
    await settleSearch(wrapper)

    expect(offered(wrapper).map((u) => u.id)).toEqual([5410])
  })

  it("resolves the pick against the list the form holds when nothing was searched yet", async () => {
    const wrapper = mountSelect([alice, zoe])

    await wrapper.setProps({modelValue: 5410})

    expect(selected(wrapper)).toMatchObject({id: 5410})
  })

  it("keeps the pick when the list the form holds no longer names it", async () => {
    const wrapper = mountSelect([alice], 7)

    await wrapper.setProps({users: [zoe]})

    expect(selected(wrapper)).toMatchObject({id: 7})
  })

  // The list arrived without the account the form named, and nothing was picked to fall back on.
  it("holds nothing when the list that arrives does not name the account the form asked for", async () => {
    const wrapper = mountSelect([], 7)

    await wrapper.setProps({users: [zoe]})

    expect(selected(wrapper)).toBeFalsy()
  })
})
