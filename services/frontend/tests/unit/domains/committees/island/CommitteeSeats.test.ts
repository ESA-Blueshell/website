import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import CommitteeSeats, {type Seat} from "@/domains/committees/island/CommitteeSeats.vue"

const users = vi.hoisted(() => ({readUser: vi.fn(), findMemberAccounts: vi.fn()}))
vi.mock("@/domains/user", () => users)

const SearchPicker = {
  name: "SearchPicker",
  props: ["options", "emptyNote", "loading", "placeholder", "testidPrefix", "remote"],
  emits: ["pick", "search"],
  template: "<div />",
}
const IconButton = {name: "IconButton", props: ["label", "testid"], emits: ["click"], template: "<button :data-testid='testid' @click=\"$emit('click')\"><slot /></button>"}

const account = (id: number, fullName: string, extra: Record<string, unknown> = {}) => ({id, fullName, email: `${id}@x.nl`, discord: null, ...extra})

const mountSeats = (seats: Seat[]) => mount(CommitteeSeats, {
  props: {"modelValue": seats, "onUpdate:modelValue": (next: Seat[]) => wrapper.setProps({modelValue: next})},
  global: {stubs: {SearchPicker, IconButton}},
})
let wrapper: ReturnType<typeof mountSeats>

beforeEach(() => {
  vi.useFakeTimers()
  users.readUser.mockReset().mockImplementation(async (id: number) => (id === 7 ? account(7, "Nelly Bee") : null))
  users.findMemberAccounts.mockReset()
})

afterEach(() => {
  vi.useRealTimers()
})

const picker = () => wrapper.getComponent(SearchPicker)

describe("who sits on a committee", () => {
  it("says so when nobody does, and names the people seated one by one", async () => {
    wrapper = mountSeats([])
    expect(wrapper.get("[data-testid=committee-edit-no-seats]").text()).toBe("Nobody is on this committee yet.")

    wrapper = mountSeats([{userId: 7, role: "Chair"}, {userId: 8, role: ""}])
    expect(wrapper.get("[data-testid=committee-edit-seat-7]").text()).toContain("…")
    await flushPromises()

    expect(wrapper.get("[data-testid=committee-edit-seat-7]").text()).toContain("Nelly Bee")
    expect(wrapper.get("[data-testid=committee-edit-seat-8]").text()).toContain("Account 8")
    expect(users.readUser).toHaveBeenCalledTimes(2)
  })

  it("searches as somebody types, leaves out who is seated, and seats who is picked", async () => {
    users.findMemberAccounts.mockResolvedValue([account(7, "Nelly Bee"), account(9, "Mo Moss", {discord: "mo#1"}), account(10, "", {email: null})])
    wrapper = mountSeats([{userId: 7, role: "Chair"}])
    await flushPromises()
    expect(picker().props("emptyNote")).toBe("")

    picker().vm.$emit("search", "  mo ")
    picker().vm.$emit("search", "mo")
    expect(picker().props("loading")).toBe(false)
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()

    expect(users.findMemberAccounts).toHaveBeenCalledTimes(1)
    expect(users.findMemberAccounts).toHaveBeenCalledWith("mo", 20)
    expect(picker().props("options")).toEqual([
      {key: "9", label: "Mo Moss", note: "mo#1"},
      {key: "10", label: "Account 10", note: undefined},
    ])
    picker().vm.$emit("pick", "9")
    picker().vm.$emit("pick", "404")
    await flushPromises()

    expect(wrapper.props("modelValue")).toEqual([{userId: 7, role: "Chair"}, {userId: 9, role: ""}])
    expect(wrapper.get("[data-testid=committee-edit-seat-9]").text()).toContain("Mo Moss")
  })

  it("says what a search found nothing new for, and forgets it when the search is cleared", async () => {
    users.findMemberAccounts.mockResolvedValueOnce([]).mockResolvedValueOnce([account(7, "Nelly Bee")])
    wrapper = mountSeats([{userId: 7, role: ""}])
    await flushPromises()

    picker().vm.$emit("search", "zz")
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    expect(picker().props("emptyNote")).toBe('Nobody found for "zz".')

    picker().vm.$emit("search", "nel")
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    expect(picker().props("emptyNote")).toBe('Everybody found for "nel" is on the committee already.')

    users.findMemberAccounts.mockResolvedValueOnce(null)
    picker().vm.$emit("search", "refused")
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    expect(picker().props("emptyNote")).toContain("The search did not go through.")
    expect(picker().props("options")).toEqual([])

    picker().vm.$emit("search", "")
    await flushPromises()
    expect(picker().props("options")).toEqual([])
    expect(picker().props("emptyNote")).toBe("")
  })

  it("takes somebody off, and keeps the answer to the newest search only", async () => {
    let answer: (found: unknown[]) => void = () => {}
    users.findMemberAccounts
      .mockImplementationOnce(() => new Promise(resolve => { answer = resolve }))
      .mockResolvedValueOnce([account(11, "Newest")])
    wrapper = mountSeats([{userId: 7, role: ""}])
    await flushPromises()

    picker().vm.$emit("search", "old")
    await vi.advanceTimersByTimeAsync(250)
    expect(picker().props("loading")).toBe(true)
    picker().vm.$emit("search", "new")
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    answer([account(12, "Stale")])
    await flushPromises()
    expect(picker().props("options")).toEqual([{key: "11", label: "Newest", note: "11@x.nl"}])

    await wrapper.get("[data-testid=committee-edit-unseat-7]").trigger("click")
    expect(wrapper.props("modelValue")).toEqual([])
    wrapper.unmount()
  })
})
