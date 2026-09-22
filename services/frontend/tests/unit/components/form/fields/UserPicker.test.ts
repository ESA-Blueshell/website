import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import UserPicker from "@/components/form/fields/UserPicker.vue"

const {mockListUsers, mockHandleNetworkError} = vi.hoisted(() => ({
  mockListUsers: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

vi.mock("@/domains/user", () => ({
  listUsers: mockListUsers,
  Role: {
    ANONYMOUS: "ANONYMOUS",
    GUEST: "GUEST",
    MEMBER: "MEMBER",
    COMMITTEE: "COMMITTEE",
    BOARD: "BOARD",
    TREASURER: "TREASURER",
    ADMIN: "ADMIN",
    SYSTEM: "SYSTEM",
  },
}))

// The api answers with inherited roles, so a board member arrives carrying MEMBER as well.
const member = {id: 1, fullName: "Member Mary", email: "mary@example.com", roles: ["ANONYMOUS", "GUEST", "MEMBER"]}
const board = {
  id: 2,
  fullName: "Board Bea",
  email: "bea@example.com",
  roles: ["ANONYMOUS", "GUEST", "MEMBER", "COMMITTEE", "BOARD"],
}
const outsider = {id: 3, fullName: "Guest Gordon", email: "gordon@example.com", roles: ["ANONYMOUS", "GUEST"]}

function picker(membersOnly: boolean) {
  const wrapper = shallowMount(UserPicker, {props: {membersOnly}})
  return (wrapper.vm as unknown as {isEligible: (u: unknown) => boolean}).isEligible
}

describe("UserPicker eligibility", () => {
  it("lets anybody be picked when the event is open to all", () => {
    const isEligible = picker(false)

    expect(isEligible(outsider)).toBe(true)
  })

  it("refuses somebody without a membership on a members-only event", () => {
    const isEligible = picker(true)

    expect(isEligible(outsider)).toBe(false)
    expect(isEligible(member)).toBe(true)
  })

  it("counts a board member, who carries the membership the api sent", () => {
    const isEligible = picker(true)

    expect(isEligible(board)).toBe(true)
  })

  it("survives an account the api answered without roles", () => {
    const isEligible = picker(true)

    expect(isEligible({id: 4, fullName: "Nobody", email: "nobody@example.com"})).toBe(false)
  })
  it("reads no user list until somebody opens it", () => {
    shallowMount(UserPicker, {props: {membersOnly: false}})

    expect(mockListUsers).not.toHaveBeenCalled()
  })
})

describe("UserPicker list", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockListUsers.mockResolvedValue([
      {id: 2, fullName: "Zoe Zander", email: "zoe@example.com", roles: ["MEMBER"]},
      {id: 1, fullName: "Ada Lovelace", email: "ada@example.com", roles: ["MEMBER"]},
    ])
  })

  it("reads the list once, on focus, and sorts it by name", async () => {
    const wrapper = shallowMount(UserPicker, {props: {}})
    const vm = wrapper.vm as any

    await vm.loadUsers()
    await vm.loadUsers()

    expect(mockListUsers).toHaveBeenCalledTimes(1)
    expect(vm.items.map((u: {id: number}) => u.id)).toEqual([1, 2])
  })

  it("sorts accounts with no name by their email, and survives an answer with neither", async () => {
    mockListUsers.mockResolvedValue([
      {id: 3, email: "zeb@example.com", roles: []},
      {id: 4, roles: []},
      {id: 5, email: "ada@example.com", roles: []},
    ])
    const vm = shallowMount(UserPicker, {props: {}}).vm as any

    await vm.loadUsers()

    expect(vm.items.map((u: {id: number}) => u.id)).toEqual([4, 5, 3])
  })

  it("holds an empty list when there are no accounts to pick from", async () => {
    mockListUsers.mockResolvedValue([])
    const vm = shallowMount(UserPicker, {props: {}}).vm as any

    await vm.loadUsers()

    expect(vm.items).toEqual([])
  })

  it("reports a refused list rather than showing an empty one", async () => {
    mockListUsers.mockRejectedValue(new Error("403"))
    const wrapper = shallowMount(UserPicker, {props: {}})
    const vm = wrapper.vm as any

    await vm.loadUsers()

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect(vm.items).toEqual([])
    expect(vm.loading).toBe(false)
  })

  it("titles a user by name and email, and falls back when either is missing", () => {
    const vm = shallowMount(UserPicker, {props: {}}).vm as any

    expect(vm.itemTitle({id: 1, fullName: "Ada", email: "ada@example.com"})).toBe("Ada (ada@example.com)")
    expect(vm.itemTitle({id: 1, fullName: "Ada"})).toBe("Ada")
    expect(vm.itemTitle({id: 9, email: "nine@example.com"})).toBe("nine@example.com (nine@example.com)")
    expect(vm.itemTitle({id: 9})).toBe("User #9")
    expect(vm.itemTitle(undefined)).toBe("")
  })
  it("waits for typing to settle before reading the list", async () => {
    vi.useFakeTimers()
    const wrapper = shallowMount(UserPicker, {props: {}})
    const vm = wrapper.vm as any

    vm.search = "ada"
    await wrapper.vm.$nextTick()
    expect(mockListUsers).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(300)
    expect(mockListUsers).toHaveBeenCalledTimes(1)
    vi.useRealTimers()
  })

  it("ignores an empty search, and asks no second time once loaded", async () => {
    vi.useFakeTimers()
    const wrapper = shallowMount(UserPicker, {props: {}})
    const vm = wrapper.vm as any

    vm.search = ""
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(300)
    expect(mockListUsers).not.toHaveBeenCalled()

    await vm.loadUsers()
    vm.search = "ada"
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(300)
    expect(mockListUsers).toHaveBeenCalledTimes(1)
    vi.useRealTimers()
  })
  it("draws a member as pickable and a non-member as refused, with the reason", async () => {
    const autocompleteStub = {
      name: "VAutocomplete",
      props: ["items", "loading", "itemTitle", "modelValue"],
      emits: ["update:focused", "update:modelValue", "update:search"],
      // Vuetify draws the slot per item it holds, so the stub does the same.
      template: `<div>
        <slot v-for="user in items" :key="user.id" name="item" :props="{title: 'row'}" :item="{raw: user}" />
      </div>`,
    }
    const listItemStub = {name: "VListItem", props: ["disabled", "subtitle"], template: "<div />"}

    const wrapper = shallowMount(UserPicker, {
      props: {membersOnly: true},
      global: {stubs: {VAutocomplete: autocompleteStub, VListItem: listItemStub}},
    })
    const vm = wrapper.vm as any
    vm.items = [
      {id: 1, fullName: "Member Mary", email: "mary@example.com", roles: ["ANONYMOUS", "GUEST", "MEMBER"]},
      {id: 3, fullName: "Guest Gordon", email: "gordon@example.com", roles: ["ANONYMOUS", "GUEST"]},
    ]
    await wrapper.vm.$nextTick()

    const rows = wrapper.findAllComponents({name: "VListItem"})
    expect(rows[0]!.props("disabled")).toBe(false)
    expect(rows[0]!.props("subtitle")).toBeUndefined()
    expect(rows[1]!.props("disabled")).toBe(true)
    expect(rows[1]!.props("subtitle")).toBe("Not a member")
  })

  it("restarts the wait when typing carries on, and requires a value when asked to", async () => {
    vi.useFakeTimers()
    const wrapper = shallowMount(UserPicker, {props: {required: true}})
    const vm = wrapper.vm as any

    vm.search = "a"
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(200)
    vm.search = "ad"
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(200)
    expect(mockListUsers).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(100)
    expect(mockListUsers).toHaveBeenCalledTimes(1)

    const rule = wrapper.findComponent({name: "VAutocomplete"}).props("rules")[0]
    expect(rule(undefined)).toBe("Required")
    expect(rule(7)).toBe(true)
    vi.useRealTimers()
  })

  it("reads the list when it is focused, and reports what was picked", async () => {
    const autocompleteStub = {
      name: "VAutocomplete",
      emits: ["update:focused", "update:modelValue", "update:search"],
      template: "<div />",
    }
    const wrapper = shallowMount(UserPicker, {
      props: {},
      global: {stubs: {VAutocomplete: autocompleteStub}},
    })
    const autocomplete = wrapper.findComponent({name: "VAutocomplete"})

    await autocomplete.vm.$emit("update:focused", false)
    expect(mockListUsers).not.toHaveBeenCalled()

    await autocomplete.vm.$emit("update:focused", true)
    expect(mockListUsers).toHaveBeenCalledTimes(1)

    await autocomplete.vm.$emit("update:modelValue", 7)
    expect(wrapper.emitted("update:modelValue")).toEqual([[7]])

    await autocomplete.vm.$emit("update:search", "ada")
    expect((wrapper.vm as any).search).toBe("ada")
  })
})
