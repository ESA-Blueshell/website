import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import UserPicker from "@/components/form/fields/UserPicker.vue"

const {mockListUsers, mockHandleNetworkError} = vi.hoisted(() => ({
  mockListUsers: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))
vi.mock("@/domains/user", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  listUsers: mockListUsers,
}))

// The api answers with inherited roles, so a board member arrives carrying MEMBER as well.
const member = {id: 1, fullName: "Member Mary", email: "mary@example.com", roles: ["GUEST", "MEMBER"]}
const board = {id: 2, fullName: "Board Bea", email: "bea@example.com", roles: ["MEMBER", "BOARD"]}
const outsider = {id: 3, fullName: "Guest Gordon", email: "gordon@example.com", roles: ["GUEST"]}

const stubs = {FormField: {template: "<div><slot /></div>"}}
const picker = (wrapper: ReturnType<typeof mount>) => wrapper.findComponent({name: "SearchPicker"})

const opened = async (props: Record<string, unknown> = {}) => {
  const wrapper = mount(UserPicker, {props, global: {stubs}})
  picker(wrapper).vm.$emit("opened")
  await flushPromises()
  return wrapper
}

const rows = (wrapper: ReturnType<typeof mount>) =>
  picker(wrapper).props("options") as Array<{key: string; label: string; note?: string; disabled?: boolean}>

describe("UserPicker", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockListUsers.mockResolvedValue([board, member, outsider])
  })

  it("reads no user list until somebody opens it", () => {
    mount(UserPicker, {global: {stubs}})

    expect(mockListUsers).not.toHaveBeenCalled()
  })

  it("reads the list once, however often it is opened, and sorts it by name", async () => {
    const wrapper = await opened()
    picker(wrapper).vm.$emit("opened")
    await flushPromises()

    expect(mockListUsers).toHaveBeenCalledTimes(1)
    expect(rows(wrapper).map(one => one.label))
      .toEqual(["Board Bea", "Guest Gordon", "Member Mary"])
  })

  it("lets anybody be chosen where the event is open to all", async () => {
    const wrapper = await opened()

    expect(rows(wrapper).every(one => one.disabled === false)).toBe(true)
  })

  it("says which people cannot be chosen where only members may be", async () => {
    const wrapper = await opened({membersOnly: true})
    const drawn = Object.fromEntries(rows(wrapper).map(one => [one.label, one]))

    expect(drawn["Member Mary"]).toMatchObject({disabled: false, note: "mary@example.com"})
    expect(drawn["Board Bea"]).toMatchObject({disabled: false})
    expect(drawn["Guest Gordon"]).toMatchObject({disabled: true, note: "Not a member"})
  })

  it("counts an account the api answered without roles as no member", async () => {
    mockListUsers.mockResolvedValue([{id: 4, fullName: "Nobody"}])
    const wrapper = await opened({membersOnly: true})

    expect(rows(wrapper)[0]).toMatchObject({disabled: true, note: "Not a member"})
  })

  it("falls back to an email, then to the number, for an account with no name", async () => {
    mockListUsers.mockResolvedValue([
      {id: 5, email: "ada@example.com", roles: ["MEMBER"]},
      {id: 6, roles: ["MEMBER"]},
    ])
    const wrapper = await opened()

    expect(rows(wrapper).map(one => one.label)).toEqual(["ada@example.com", "User #6"])
  })

  it("holds an empty list when there are no accounts to pick from", async () => {
    mockListUsers.mockResolvedValue([])
    const wrapper = await opened()

    expect(rows(wrapper)).toEqual([])
  })

  it("reports a refused list rather than showing an empty one", async () => {
    mockListUsers.mockRejectedValue(new Error("403"))
    const wrapper = await opened()

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect(rows(wrapper)).toEqual([])
    expect(picker(wrapper).props("loading")).toBe(false)
  })

  it("carries the chosen id, and reports the one that was picked", async () => {
    const wrapper = await opened({modelValue: 2})
    expect(picker(wrapper).props("selectedKey")).toBe("2")

    picker(wrapper).vm.$emit("pick", "3")
    await flushPromises()

    expect(wrapper.emitted("update:modelValue")).toEqual([[3]])
  })
})
