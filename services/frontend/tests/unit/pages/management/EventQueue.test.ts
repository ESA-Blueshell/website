import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import EventQueue from "@/pages/management/EventQueue.vue"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({listApprovalQueue: vi.fn(), approveEvent: vi.fn()}))
const {mockStore} = vi.hoisted(() => ({mockStore: {commit: vi.fn(), getters: {} as Record<string, unknown>}}))

vi.mock("@/plugins/store", () => ({default: mockStore}))
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const event = (id: number, title: string, fields: Record<string, unknown> = {}) => ({
  id, title, startTime: "2099-05-01T18:00:00Z", endTime: "2099-05-01T22:00:00Z", approved: false, awaitingReapproval: false,
  membersOnly: false, signUp: false, signUpCount: 0, gameCodes: [], pingedRoles: [], createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z", version: 0, ...fields,
})

describe("the events to approve", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(EventQueue)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.listApprovalQueue.mockResolvedValue({status: 200, data: [
      {event: event(1, "Pub quiz"), reapproval: false, changes: []},
      {event: event(2, "LAN party", {awaitingReapproval: true}), reapproval: true, changes: ["TITLE", "TIMES"]},
      {event: event(3, "Old", {announced: true}), reapproval: true, changes: []},
    ]})
    api.approveEvent.mockResolvedValue({status: 200, data: event(1, "Pub quiz", {approved: true})})
  })

  afterEach(() => unmountAll(wrappers, "EventQueuePage"))

  it("lists new events and re-approvals apart, a re-approval with what changed", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="event-queue-row-1"]').text()).toContain("Pub quiz")
    expect(wrapper.get('[data-testid="event-queue-new-1"]').text()).toBe("Awaiting approval")
    expect(wrapper.get('[data-testid="event-queue-row-2"]').text()).toContain("LAN party")
    expect(wrapper.get('[data-testid="event-queue-reapproval-2"]').text()).toBe("Awaiting re-approval")
    expect(wrapper.findAll('[data-testid^="event-queue-row-"]')[0]!.attributes("data-testid")).toBe("event-queue-row-1")
    expect(wrapper.findComponent({name: "FactList"}).text()).toContain("1 new, 2 changed since approval")
    expect(wrapper.get('[data-testid="event-queue-changes-2"]').text()).toBe("Changed: title and times")
    expect(wrapper.get('[data-testid="event-queue-changes-3"]').text()).toBe("Changed since it was approved")
    expect(wrapper.get('[data-testid="event-queue-changes-1"]').text()).toBe("New")
    expect(wrapper.get('[data-testid="event-queue-open-1"]').attributes("to")).toBe("/events/1")
    expect(await sortByEveryHead(wrapper)).toBe(4)
  })

  it("draws each waiting event as a row on a phone, with approving and opening at its end", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = await mount()
    vi.unstubAllGlobals()

    expect(wrapper.get('[data-testid="event-queue-row-1"]').text()).toContain("Awaiting approval")
    expect(wrapper.get('[data-testid="event-queue-row-2"]').text()).toContain("Changed: title and times")
    expect(wrapper.get('[data-testid="event-queue-open-1"]').attributes("to")).toBe("/events/1")
    await wrapper.get('[data-testid="event-queue-approve-1"]').trigger("click")
    await settle()
    expect(wrapper.findComponent({name: "AnnounceDialog"}).props("open")).toBe(true)
  })

  it("approves as the event page does: asking when the post goes out, and not at all when cancelled", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="event-queue-approve-1"]').trigger("click")
    await settle()
    wrapper.findComponent({name: "AnnounceDialog"}).vm.$emit("answer", null)
    await settle()
    expect(api.approveEvent).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="event-queue-approve-1"]').trigger("click")
    await settle()
    wrapper.findComponent({name: "AnnounceDialog"}).vm.$emit("answer", "NEXT_MORNING")
    await settle()
    expect(api.approveEvent).toHaveBeenCalledWith({path: {id: 1}, query: {approved: true, announce: "NEXT_MORNING"}, throwOnError: true})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Pub quiz is approved.")
    expect(api.listApprovalQueue).toHaveBeenCalledTimes(2)

    await wrapper.get('[data-testid="event-queue-approve-3"]').trigger("click")
    await settle()
    expect(api.approveEvent).toHaveBeenLastCalledWith({path: {id: 3}, query: {approved: true}, throwOnError: true})
  })

  it("says when approving fails, when nothing waits, and when the queue cannot be read", async () => {
    api.approveEvent.mockRejectedValue(new Error("409"))
    const wrapper = await mount()
    await wrapper.get('[data-testid="event-queue-approve-3"]').trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Old could not be approved.")

    api.listApprovalQueue.mockResolvedValue({status: 200, data: []})
    expect((await mount()).get('[data-testid="event-queue-empty"]').text()).toBe("Nothing is waiting for the board.")
    api.listApprovalQueue.mockResolvedValue({status: 403, error: null, response: {status: 403}})
    expect((await mount()).get('[data-testid="event-queue-unreadable"]').text()).toContain("could not be read")
  })
})
