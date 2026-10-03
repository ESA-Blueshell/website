import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import InboxMessage from "@/pages/management/InboxMessage.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findConversation: vi.fn(),
  replyToMessage: vi.fn(),
  markMessageHandled: vi.fn(),
  findReplyToOptions: vi.fn(),
}))
const {mockStore, route} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {isAdmin: true} as Record<string, unknown>},
  route: {params: {id: "2"}},
}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => route,
}))

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const message = (fields: Record<string, unknown> = {}) => ({
  id: 2, fromAddress: "lars@example.com", fromName: "Lars", subject: "Re: Your contribution", receivedAt: "2026-09-29T11:20:00Z",
  state: "NEW", automatic: false, senderUserId: 5, senderName: "Lars Mulder", ...fields,
})
const conversation = (fields: Record<string, unknown> = {}, items: unknown[] = []) => ({
  message: message(fields),
  items: [
    {kind: "SENT", at: "2026-09-29T09:40:00Z", subject: "Your contribution", emailId: 9},
    {kind: "RECEIVED", at: "2026-09-29T11:20:00Z", subject: "Re: Your contribution", body: "I already paid.", fromAddress: "lars@example.com", inboxMessageId: 2},
    ...items,
  ],
  earlier: [
    {kind: "SENT", at: "2026-09-21T10:00:00Z", subject: "Welcome to Blueshell", emailId: 8},
    {kind: "RECEIVED", at: "2026-09-12T10:00:00Z", subject: "", inboxMessageId: 1},
  ],
})

describe("a conversation in the inbox", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(InboxMessage)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    route.params.id = "2"
    api.findConversation.mockResolvedValue({status: 200, data: conversation()})
    api.findReplyToOptions.mockResolvedValue({status: 200, data: ["board@b.nl", "treasurer@b.nl"]})
    api.replyToMessage.mockResolvedValue({status: 200, data: conversation({state: "REPLIED", handledAt: "2026-10-01T10:00:00Z", handledByName: "Alice Board"},
      [{kind: "REPLY", at: "2026-10-01T10:00:00Z", body: "Thanks!", inboxMessageId: 2, writtenByName: "Alice Board"}])})
    api.markMessageHandled.mockResolvedValue({status: 200, data: conversation({state: "HANDLED", handledAt: "2026-10-01T10:00:00Z"})})
  })

  afterEach(() => unmountAll(wrappers, "InboxMessagePage"))

  it("shows the conversation oldest first, who it is from and earlier mail with them", async () => {
    const wrapper = await mount()

    expect(api.findConversation).toHaveBeenCalledWith({path: {id: 2}})
    expect(wrapper.get('[data-testid="inbox-message-state"]').text()).toBe("Inbox · New")
    expect(wrapper.text()).toContain("Lars Mulder and the site, 2 emails")
    expect(wrapper.get('[data-testid="inbox-message-item-0"]').text()).toContain("The site · Your contribution")
    expect(wrapper.get('[data-testid="inbox-message-item-0"] .conversation__link').attributes("to")).toBe("/management/mail/sent/9")
    expect(wrapper.get('[data-testid="inbox-message-item-1"]').text()).toContain("I already paid.")
    expect(wrapper.get('[data-testid="inbox-message-from"]').text()).toContain("Open Lars Mulder")
    const earlier = wrapper.findAll('[data-testid="inbox-message-earlier"] li > *:first-child')
    expect(earlier.map((one) => one.attributes("to"))).toEqual(["/management/mail/sent/8", "/management/mail/inbox/1"])
    expect(earlier[1].text()).toBe("(no subject)")
  })

  it("sends a reply to the chosen address and shows it on the conversation", async () => {
    const wrapper = await mount()
    expect(wrapper.get('[data-testid="inbox-send-reply"]').attributes("disabled")).toBeDefined()

    wrapper.findComponent({name: "MarkdownEditor"}).vm.$emit("update:modelValue", "Thanks!")
    wrapper.findComponent({name: "SearchPicker"}).vm.$emit("pick", "treasurer@b.nl")
    await settle()
    await wrapper.get("form").trigger("submit")
    await settle()

    expect(api.replyToMessage).toHaveBeenCalledWith({path: {id: 2}, body: {message: "Thanks!", replyTo: "treasurer@b.nl"}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "The reply is on its way. It shows in Sent.")
    expect(wrapper.get('[data-testid="inbox-message-item-2"]').text()).toContain("Alice Board, from the site")
    expect(wrapper.get('[data-testid="inbox-message-handled"]').text()).toContain("by Alice Board")
    expect(wrapper.find('[data-testid="inbox-mark-handled"]').exists()).toBe(false)
  })

  it("marks a message handled, says why a write was refused, and says when there is no such message", async () => {
    const wrapper = await mount()
    api.markMessageHandled.mockResolvedValueOnce({status: 404, error: {code: "InboxMessageNotFound"}})
    await wrapper.get('[data-testid="inbox-mark-handled"]').trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="inbox-message-failure"]').text()).toBe("That message is no longer in the inbox.")

    await wrapper.get('[data-testid="inbox-mark-handled"]').trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="inbox-message-state"]').text()).toBe("Inbox · Handled")
    expect(wrapper.find('[data-testid="inbox-message-failure"]').exists()).toBe(false)

    api.findConversation.mockResolvedValueOnce({status: 404, error: {code: "InboxMessageNotFound"}})
    route.params.id = "7"
    const missing = await mount()
    expect(missing.get('[data-testid="inbox-message-missing"]').text()).toContain("no message 7")
  })

  it("names a sender it does not know by their address, without a link, and an empty earlier list", async () => {
    api.findConversation.mockResolvedValue({status: 200, data: {...conversation({senderUserId: null, senderName: null, fromName: null, subject: ""}), earlier: []}})
    const wrapper = await mount()

    expect(wrapper.get("h1").text()).toBe("(no subject)")
    expect(wrapper.get('[data-testid="inbox-message-from"]').text()).not.toContain("Open")
    expect(wrapper.text()).toContain("Earlier mail with lars@example.com")
    expect(wrapper.text()).toContain("None.")
  })
})
