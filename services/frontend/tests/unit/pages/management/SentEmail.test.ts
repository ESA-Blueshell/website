import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import SentEmail from "@/pages/management/SentEmail.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const {mockFind, mockPreview, mockRetry, mockResend, mockStore, mockRoute} = vi.hoisted(() => ({
  mockFind: vi.fn(),
  mockPreview: vi.fn(),
  mockRetry: vi.fn(),
  mockResend: vi.fn(),
  mockStore: {commit: vi.fn(), getters: {isAdmin: true} as Record<string, unknown>},
  mockRoute: {params: {id: "7"} as Record<string, string>},
}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => mockRoute,
}))

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findEmail: mockFind,
  previewSentEmail: mockPreview,
  retry1: mockRetry,
  resend: mockResend,
}))

const bounced = {
  id: 7, subject: "Your contribution", emailType: "email.contribution-reminder", recipientEmail: "lars@example.com",
  recipientName: "Lars Mulder", deliveryStatus: "BOUNCED", attempts: 1, jobExecutionId: 48, initiatedByUserId: 3,
  messageId: "<m@b.nl>", errorReason: "550 5.1.1 no such mailbox", createdAt: "2026-09-29T09:39:00Z",
  sentAt: "2026-09-29T09:40:00Z", updatedAt: "2026-09-29T09:41:00Z", previewable: true, resentFromId: 2,
}

describe("the one email page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(SentEmail)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isAdmin = true
    mockRoute.params = {id: "7"}
    mockFind.mockResolvedValue({status: 200, data: {email: bounced, resends: [{...bounced, id: 8, deliveryStatus: "QUEUED"}]}})
    mockPreview.mockResolvedValue({status: 200, data: {subject: "Your contribution", html: "<p>Hi Lars</p>", recipientEmail: "", recipientName: ""}})
    mockResend.mockResolvedValue({status: 200, data: {...bounced, id: 9}})
  })

  afterEach(() => {
    unmountAll(wrappers, "SentEmailPage")
  })

  it("tells what happened, shows the bounce and the email as sent, and links the person, the job and its resends", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="sent-email-status"]').text()).toBe("Bounced")
    expect(wrapper.get('[data-testid="sent-email-problem"]').text()).toContain("550 5.1.1 no such mailbox")
    expect(wrapper.get('[data-testid="sent-email-person-fix"]').attributes("to")).toBe("/management/users?search=lars%40example.com")
    expect(wrapper.get('[data-testid="sent-email-timeline"]').text()).toContain("Bounced")
    expect(wrapper.get('[data-testid="sent-email-as-sent"]').attributes("srcdoc")).toContain("Hi Lars")
    expect(wrapper.get('[data-testid="sent-email-resends"]').text()).toContain("Queued")
    expect(wrapper.get('[data-testid="sent-email-resent-from"]').attributes("to")).toBe("/management/mail/sent/2")
    expect(wrapper.get('[data-testid="sent-email-job"]').text()).toContain("Job #48")
    expect(wrapper.get('[data-testid="sent-email-job"]').text()).toContain("user #3")
    expect(wrapper.find('[data-testid="sent-email-retry"]').exists()).toBe(false)

    await wrapper.get('[data-testid="sent-email-resend"]').trigger("click")
    await settle()
    expect(mockResend).toHaveBeenCalledWith({path: {id: 7}})
    expect(mockFind).toHaveBeenCalledTimes(2)
  })

  it("retries a failed one, says why a retry was refused, and shows the job as text to the rest of the board", async () => {
    mockStore.getters.isAdmin = false
    mockFind.mockResolvedValue({status: 200, data: {email: {...bounced, deliveryStatus: "FAILED", errorReason: null, errorType: null, resentFromId: null, messageId: null, initiatedByUserId: null}, resends: []}})
    mockPreview.mockResolvedValue({status: 404, error: {}})
    mockRetry.mockResolvedValue({status: 400, error: {detail: "Linked job is not FAILED or DEAD"}})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="sent-email-problem"]').text()).toContain("The mail server gave no reason.")
    expect(wrapper.find('[data-testid="sent-email-no-body"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="sent-email-job"]').text()).toBe("Job #48")
    await wrapper.get('[data-testid="sent-email-retry"]').trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Linked job is not FAILED or DEAD")
  })

  it("says when there is no such email", async () => {
    mockFind.mockResolvedValue({status: 404, error: {}})
    expect((await mount()).find('[data-testid="sent-email-missing"]').exists()).toBe(true)
  })
})
