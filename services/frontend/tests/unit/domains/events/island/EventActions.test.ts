import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import EventActions from "@/domains/events/island/EventActions.vue"

const {mockApprove, mockDelete, mockWithdraw, mockPush, mockCommit, getters, mockNetworkError} = vi.hoisted(() => ({
  mockApprove: vi.fn(),
  mockDelete: vi.fn(),
  mockWithdraw: vi.fn(),
  mockPush: vi.fn(),
  mockCommit: vi.fn(),
  getters: {isMember: true, isBoard: true, getGuestData: null as null | {accessToken: string}},
  mockNetworkError: vi.fn(),
}))

vi.mock("@/domains/events/adapters/events", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  setEventApproved: mockApprove,
  deleteEvent: mockDelete,
}))
vi.mock("@/domains/events/adapters/signUps", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  withdrawSignUp: mockWithdraw,
}))
vi.mock("@/plugins/store", () => ({default: {getters, commit: mockCommit}}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockNetworkError}))
vi.mock("vue-router", async (importOriginal) => ({...(await importOriginal<object>()), useRouter: () => ({push: mockPush})}))

const soon = new Date(Date.now() + 3 * 86_400_000).toISOString()
const event = (over: Record<string, unknown> = {}) => ({
  id: 7, title: "LAN", startTime: soon, endTime: soon, approved: true, signUp: true,
  signUpCount: 3, signUpLimit: 24, membersOnly: false, committeeId: 2, ...over,
})

const mountActions = (props: Record<string, unknown> = {}) =>
  mount(EventActions, {
    props: {event: event(), committees: [], signUps: [], ...props},
    global: {stubs: {ConfirmDialog: {name: "ConfirmDialog", props: ["open", "question", "failure", "working"], emits: ["confirm", "update:open"], template: "<div />"}}},
  })

const signUpButton = (wrapper: ReturnType<typeof mountActions>) => wrapper.get("[data-testid=event-signup-toggle-btn-7]")

describe("what can be done with an event where it is listed", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.assign(getters, {isMember: true, isBoard: true, getGuestData: null})
  })

  it("offers the board approval, the sign-ups, editing and deleting", async () => {
    mockApprove.mockResolvedValue(event({approved: false}))
    const wrapper = mountActions()

    await wrapper.get("[data-testid=event-approve-btn-7]").trigger("click")
    await flushPromises()
    expect(mockApprove).toHaveBeenCalledWith(7, false)
    expect(wrapper.emitted("update:event")?.[0]).toEqual([event({approved: false})])

    await wrapper.get("[data-testid=event-signups-btn-7]").trigger("click")
    await wrapper.get("[data-testid=event-edit-btn-7]").trigger("click")
    expect(mockPush.mock.calls).toEqual([["/events/signups/7"], ["/events/edit/7"]])
  })

  it("says whether it is approved, and lets only the board change that, before it starts", () => {
    getters.isBoard = false
    const committee = mountActions({event: event({approved: false}), committees: [{id: 2, name: "Events"}]})
    const approval = committee.get("[data-testid=event-approve-btn-7]")

    expect(approval.text()).toBe("Awaiting approval")
    expect((approval.element as HTMLButtonElement).disabled).toBe(true)
    getters.isBoard = true
    expect(mountActions({event: event({approved: true})}).get("[data-testid=event-approve-btn-7]").text()).toBe("Approved")
  })

  it("offers nobody outside the committee or the board a way to manage it", () => {
    getters.isBoard = false

    expect(mountActions({committees: [{id: 9, name: "Other"}]}).find("[data-testid=event-edit-btn-7]").exists()).toBe(false)
  })

  it("reads no sign-ups for an event that takes none", () => {
    const wrapper = mountActions({event: event({signUp: false})})

    expect((wrapper.get("[data-testid=event-signups-btn-7]").element as HTMLButtonElement).disabled).toBe(true)
    expect(wrapper.find("[data-testid=event-signup-toggle-btn-7]").exists()).toBe(false)
  })

  it("deletes after asking, and says so where the api refuses", async () => {
    const wrapper = mountActions()
    await wrapper.get("[data-testid=event-delete-btn-7]").trigger("click")
    const dialog = wrapper.getComponent({name: "ConfirmDialog"})
    expect(dialog.props("open")).toBe(true)

    mockDelete.mockRejectedValueOnce(new Error("403"))
    dialog.vm.$emit("confirm")
    await flushPromises()
    expect(dialog.props("failure")).toBe("Couldn't delete “LAN”")
    expect(wrapper.emitted("delete:event")).toBeUndefined()

    mockDelete.mockResolvedValueOnce(undefined)
    dialog.vm.$emit("confirm")
    await flushPromises()
    expect(wrapper.emitted("delete:event")?.[0]).toEqual([7])
    expect(mockCommit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Deleted “LAN”")
    expect(dialog.props("open")).toBe(false)

    dialog.vm.$emit("update:open", true)
    await flushPromises()
    expect(dialog.props("open")).toBe(true)
  })

  it("opens and closes the sign-up form the listing draws", async () => {
    const wrapper = mountActions()

    expect(signUpButton(wrapper).text()).toBe("Sign up")
    await signUpButton(wrapper).trigger("click")
    expect(wrapper.emitted("update:signing")?.at(-1)).toEqual([true])
    await wrapper.setProps({signing: true})
    expect(signUpButton(wrapper).text()).toBe("Close")
  })

  it("edits a sign-up to an event that asks questions", () => {
    const wrapper = mountActions({
      event: event({signUpForm: {questions: [{id: 1}]}}),
      signUps: [{id: 40, eventId: 7}],
    })

    expect(signUpButton(wrapper).text()).toBe("Edit sign-up")
  })

  it("signs out at once where there is nothing to edit, carrying the guest's token", async () => {
    getters.getGuestData = {accessToken: "guest-token"}
    mockWithdraw.mockResolvedValue(undefined)
    const wrapper = mountActions({signUps: [{id: 40, eventId: 7}]})

    expect(signUpButton(wrapper).text()).toBe("Sign me out")
    await signUpButton(wrapper).trigger("click")
    await flushPromises()

    expect(mockWithdraw).toHaveBeenCalledWith(40, "guest-token")
    expect(wrapper.emitted("delete:signUp")?.[0]).toEqual([40])
    expect(wrapper.emitted("update:signing")).toBeUndefined()
  })

  it("reports a sign-out the api refused, and keeps the sign-up", async () => {
    mockWithdraw.mockRejectedValue(new Error("500"))
    const wrapper = mountActions({signUps: [{id: 40, eventId: 7}]})

    await signUpButton(wrapper).trigger("click")
    await flushPromises()

    expect(mockWithdraw).toHaveBeenCalledWith(40, null)
    expect(mockNetworkError).toHaveBeenCalled()
    expect(wrapper.emitted("delete:signUp")).toBeUndefined()
  })

  it.each([
    [{approved: false}, true, "This event is waiting for approval"],
    [{membersOnly: true}, false, "This event is for members"],
    [{startTime: "2020-01-01T00:00:00Z"}, true, "This event has started"],
    [{signUpDeadline: "2020-01-01T00:00:00Z"}, true, "Sign-ups have closed"],
    [{signUpCount: 24}, true, "This event is full"],
  ])("refuses a sign-up and says why: %o", (over, member, why) => {
    getters.isMember = member
    const button = signUpButton(mountActions({event: event(over)}))

    expect((button.element as HTMLButtonElement).disabled).toBe(true)
    expect(button.attributes("title")).toBe(why)
  })

  it("keeps a full event's sign-up open to the one already signed up", () => {
    const button = signUpButton(mountActions({event: event({signUpCount: 24}), signUps: [{id: 40, eventId: 7}]}))

    expect((button.element as HTMLButtonElement).disabled).toBe(false)
  })
})
