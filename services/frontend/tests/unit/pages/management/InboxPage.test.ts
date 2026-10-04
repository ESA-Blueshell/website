import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import InboxPage from "@/pages/management/InboxPage.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({findInbox: vi.fn(), findInboxCounts: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const entry = (fields: Record<string, unknown>) => ({
  id: 1, fromAddress: "lars@example.com", fromName: "Lars", subject: "Re: Your contribution", receivedAt: "2026-09-29T11:20:00Z",
  state: "NEW", automatic: false, ...fields,
})

describe("the Inbox page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(InboxPage)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findInbox.mockResolvedValue({status: 200, data: {content: [
      entry({id: 1, senderUserId: 5, senderName: "Lars Mulder", answers: {emailId: 9, emailType: "email.contribution-reminder"}}),
      entry({id: 2, fromAddress: "info@sponsor.example", fromName: null, toAddress: "partners@esa-blueshell.nl"}),
      entry({id: 3, automatic: true, subject: "Automatic reply: away"}),
      entry({id: 4, state: "REPLIED", handledBy: 6, handledByName: "Alice Board"}),
    ], page: {totalElements: 4, totalPages: 2}}})
    api.findInboxCounts.mockResolvedValue({status: 200, data: {new: 3, oldestNewAt: "2026-09-27T10:02:00Z", done: 142, automatic: 24}})
  })

  afterEach(() => unmountAll(wrappers, "InboxPage"))

  it("lists what arrived with its sender, what it answers and its state, and counts the inbox", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="inbox-new"]').text()).toContain("3")
    expect(wrapper.get('[data-testid="inbox-facts"]').text()).toContain("Oldest from")
    expect(wrapper.get('[data-testid="inbox-done"]').text()).toContain("142")
    expect(wrapper.get('[data-testid="inbox-automatic"]').text()).toContain("24")
    expect(wrapper.get('[data-testid="inbox-row-1"]').text()).toContain("Lars Mulder")
    expect(wrapper.get('[data-testid="inbox-follows-1"]').text()).toBe("Answers contribution reminder")
    expect(wrapper.get('[data-testid="inbox-row-2"]').text()).toContain("Unknown sender")
    expect(wrapper.get('[data-testid="inbox-follows-2"]').text()).toBe("To partners@")
    expect(wrapper.get('[data-testid="inbox-state-3"]').text()).toBe("Automatic reply")
    expect(wrapper.get('[data-testid="inbox-row-4"]').text()).toContain("Alice Board")
  })

  it("pages, refreshes, searches, and says when nothing arrived", async () => {
    const wrapper = await mount()
    await wrapper.get('[data-testid="inbox-next"]').trigger("click")
    await settle()
    expect(api.findInbox).toHaveBeenLastCalledWith({query: {page: 1}})
    await wrapper.get('[data-testid="inbox-previous"]').trigger("click")
    await wrapper.get('[data-testid="inbox-refresh"]').trigger("click")
    wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "lars")
    await vi.waitFor(() => expect(api.findInbox).toHaveBeenLastCalledWith({query: {page: 0, search: "lars"}}))

    api.findInbox.mockResolvedValue({status: 500, error: {}})
    api.findInboxCounts.mockResolvedValue({status: 500, error: {}})
    const empty = await mount()
    expect(empty.find('[data-testid="inbox-empty"]').exists()).toBe(true)
    expect(empty.get('[data-testid="inbox-facts"]').text()).toContain("Nothing waits")
  })
})
