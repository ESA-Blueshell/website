import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {defineComponent, h, type Ref, ref} from "vue"
import {useEventReader} from "@/domains/events/island/useEventReader"

const {getters, mockCommit, mockOwn, mockByToken, mockAll, mockMine, mockNetworkError} = vi.hoisted(() => ({
  getters: {isLoggedIn: false, isBoard: false, getLogin: null as null | {userId?: number}, getGuestData: null as null | {accessToken: string}},
  mockCommit: vi.fn(),
  mockOwn: vi.fn(),
  mockByToken: vi.fn(),
  mockAll: vi.fn(),
  mockMine: vi.fn(),
  mockNetworkError: vi.fn(),
}))

vi.mock("vuex", async (importOriginal) => ({...(await importOriginal<object>()), useStore: () => ({getters, commit: mockCommit})}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockNetworkError}))
vi.mock("@/domains/committees", () => ({listCommittees: mockAll, listMyCommittees: mockMine}))
vi.mock("@/domains/events/adapters/signUps", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  listOwnSignUps: mockOwn,
  listSignUpsByAccessToken: mockByToken,
}))

function read(token?: Ref<string | null>) {
  let held: ReturnType<typeof useEventReader> | undefined
  mount(defineComponent({setup() {
    held = token === undefined ? useEventReader() : useEventReader(token)
    return () => h("div")
  }}))
  return held!
}

describe("who is reading an events page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.assign(getters, {isLoggedIn: false, isBoard: false, getLogin: null, getGuestData: null})
  })

  it("reads a member's own sign-ups and their committees, dropping any the api half answered", async () => {
    Object.assign(getters, {isLoggedIn: true, getLogin: {userId: 7}})
    mockOwn.mockResolvedValue([{id: 40}])
    mockMine.mockResolvedValue([{id: 2, name: "4FunCie"}, {id: "x", name: "Broken"}, {id: 3}])
    const reader = read()
    await flushPromises()

    expect(mockOwn).toHaveBeenCalledWith(7, expect.any(String))
    expect(reader.signUps.value).toEqual([{id: 40}])
    expect(reader.committees.value).toEqual([{id: 2, name: "4FunCie"}])
  })

  it("reads every committee for the board", async () => {
    Object.assign(getters, {isLoggedIn: true, isBoard: true, getLogin: {userId: 7}})
    mockOwn.mockResolvedValue([])
    mockAll.mockResolvedValue([{id: 1, name: "Board"}])
    const reader = read()
    await flushPromises()

    expect(mockAll).toHaveBeenCalled()
    expect(reader.committees.value).toEqual([{id: 1, name: "Board"}])
  })

  it("reads a guest's sign-ups by the token their link carried, and remembers the guest", async () => {
    mockByToken.mockResolvedValue([{id: 41, guest: {name: "Bob"}}])
    const reader = read(ref("link-token"))
    await flushPromises()

    expect(mockByToken).toHaveBeenCalledWith("link-token")
    expect(reader.signUps.value).toEqual([{id: 41, guest: {name: "Bob"}}])
    expect(mockCommit).toHaveBeenCalledWith("saveGuestData", {name: "Bob", accessToken: "link-token"})
    expect(reader.committees.value).toEqual([])
  })

  it("prefers the guest the session remembers, and remembers nobody from an empty answer", async () => {
    getters.getGuestData = {accessToken: "session-token"}
    mockByToken.mockResolvedValue([])
    read(ref("link-token"))
    await flushPromises()

    expect(mockByToken).toHaveBeenCalledWith("session-token")
    expect(mockCommit).not.toHaveBeenCalled()
  })

  it("reads nothing for somebody nobody knows, and a logged-in reader without an id as nobody", async () => {
    const stranger = read()
    Object.assign(getters, {isLoggedIn: true, getLogin: {}})
    mockMine.mockResolvedValue([])
    const nameless = read()
    await flushPromises()

    expect(stranger.signUps.value).toEqual([])
    expect(nameless.signUps.value).toEqual([])
    expect(mockOwn).not.toHaveBeenCalled()
  })

  it("reports what it could not read", async () => {
    Object.assign(getters, {isLoggedIn: true, getLogin: {userId: 7}})
    mockOwn.mockRejectedValue(new Error("500"))
    mockMine.mockRejectedValue(new Error("500"))
    read()
    await flushPromises()

    expect(mockNetworkError).toHaveBeenCalledTimes(2)
  })
})
