import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import UserSelect from "@/components/form/fields/UserSelect.vue"
import {mountInApp, settle, unmountAll} from "../../../helpers/testUtils"

const {mockFindUsers, mockFindUserById} = vi.hoisted(() => ({
  mockFindUsers: vi.fn(),
  mockFindUserById: vi.fn(),
}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {...actual, findUsers: mockFindUsers, findUserById: mockFindUserById}
})

const wrappers: VueWrapper[] = []

function mountSelect(modelValue?: number) {
  const wrapper = mountInApp(UserSelect, {props: {modelValue}})
  wrappers.push(wrapper)
  return wrapper
}

function answerWith(content: unknown[], totalElements = content.length) {
  mockFindUsers.mockResolvedValue({data: {content, page: {totalElements}}})
}

/** Runs the field's debounce out, then lets the request it started settle. */
async function afterTyping(wrapper: VueWrapper) {
  await vi.runAllTimersAsync()
  await settle()
  return wrapper
}

async function type(wrapper: VueWrapper, term: string) {
  await wrapper.findComponent({name: "VAutocomplete"}).setValue(term, "search")
  return afterTyping(wrapper)
}

function items(wrapper: VueWrapper) {
  return wrapper.findComponent({name: "VAutocomplete"}).props("items") as Array<{ id: number }>
}

describe("UserSelect", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mockFindUsers.mockReset()
    mockFindUserById.mockReset()
    answerWith([])
    mockFindUserById.mockResolvedValue({data: {id: 7, fullName: "Roos Kruk"}})
  })

  afterEach(() => {
    unmountAll(wrappers, "UserSelect")
    vi.useRealTimers()
  })

  it("asks the api for the term the reader typed", async () => {
    answerWith([{id: 42, fullName: "Roos Kruk"}])

    const wrapper = await type(mountSelect(), "roos kruk")

    expect(mockFindUsers).toHaveBeenLastCalledWith({
      query: {search: "roos kruk", page: 0, size: 20},
    })
    expect(items(wrapper).map((u) => u.id)).toContain(42)
  })

  it("offers a member no page of users would have reached", async () => {
    // The whole point: the answer is built for this term, so where the member sorts in the
    // full table decides nothing.
    answerWith([{id: 5410, fullName: "Zoë Zwart"}], 5410)

    const wrapper = await type(mountSelect(), "zwart")

    expect(items(wrapper).map((u) => u.id)).toEqual([5410])
  })

  it("says there is more to find while the api holds more than it showed", async () => {
    answerWith([{id: 1, fullName: "Jan Jansen"}], 40)

    const wrapper = await type(mountSelect(), "jan")

    expect(wrapper.findComponent({name: "VAutocomplete"}).props("hint")).toContain("keep typing")
  })

  it("reads the picked member by id, so an edited committee shows who is on it", async () => {
    const wrapper = mountSelect(7)
    await afterTyping(wrapper)

    expect(mockFindUserById).toHaveBeenCalledWith({path: {userId: 7}})
    expect(items(wrapper).map((u) => u.id)).toContain(7)
  })

  it("keeps the picked member in the list when a later search excludes them", async () => {
    const wrapper = mountSelect(7)
    await afterTyping(wrapper)
    answerWith([{id: 9, fullName: "Someone Else"}])

    await type(wrapper, "someone")

    expect(items(wrapper).map((u) => u.id)).toContain(7)
  })

  it("ignores an answer overtaken by a later one", async () => {
    let releaseFirst: (value: unknown) => void = () => {}
    mockFindUsers.mockReturnValueOnce(new Promise((resolve) => {
      releaseFirst = resolve
    }))
    const wrapper = mountSelect()

    await wrapper.findComponent({name: "VAutocomplete"}).setValue("ja", "search")
    await vi.runAllTimersAsync()
    answerWith([{id: 2, fullName: "Jan Jansen"}])
    await type(wrapper, "jan")
    releaseFirst({data: {content: [{id: 1, fullName: "Stale"}], page: {totalElements: 1}}})
    await settle()

    expect(items(wrapper).map((u) => u.id)).toEqual([2])
  })

  it("does not read a fresh row's 0 as somebody who is picked", async () => {
    // Passed through as a selection it lands in the field as the text "0", which then prefixes
    // whatever gets typed and searches for a name nobody has.
    const wrapper = mountSelect(0)
    await afterTyping(wrapper)

    expect(wrapper.findComponent({name: "VAutocomplete"}).props("modelValue") ?? undefined).toBeUndefined()
    expect(mockFindUserById).not.toHaveBeenCalled()
  })

  it("stays empty rather than guessing when the api refuses", async () => {
    mockFindUsers.mockResolvedValue({error: {status: 403}})

    const wrapper = await type(mountSelect(), "roos")

    expect(items(wrapper)).toEqual([])
  })
})
