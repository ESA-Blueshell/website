import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import SentEmails from "@/pages/management/SentEmails.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const {mockList, mockStats, mockRetry, mockResend, mockStore} = vi.hoisted(() => ({
  mockList: vi.fn(),
  mockStats: vi.fn(),
  mockRetry: vi.fn(),
  mockResend: vi.fn(),
  mockStore: {commit: vi.fn(), getters: {isAdmin: true} as Record<string, unknown>},
}))

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  list1: mockList,
  getStats1: mockStats,
  retry1: mockRetry,
  resend: mockResend,
}))

const email = (fields: Record<string, unknown>) => ({
  id: 1, subject: "Your contribution", emailType: "email.contribution-reminder", recipientEmail: "sam@example.com",
  recipientName: "Sam", deliveryStatus: "SENT", attempts: 1, jobExecutionId: 9, createdAt: "2026-09-29T08:00:00Z",
  sentAt: "2026-09-29T08:01:00Z", previewable: true, ...fields,
})

const page = (rows: unknown[], totalPages = 1) => ({status: 200, data: {content: rows, page: {totalElements: rows.length, totalPages}}})

describe("the Sent page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(SentEmails)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockList.mockResolvedValue(page([
      email({id: 1, deliveryStatus: "QUEUED", sentAt: null}),
      email({id: 2, deliveryStatus: "FAILED"}),
      email({id: 3, deliveryStatus: "OPENED"}),
      email({id: 4, deliveryStatus: "DELIVERED", jobExecutionId: null}),
    ], 2))
    mockStats.mockResolvedValue({status: 200, data: {
      totalCount: 110, queuedCount: 10, sentCount: 10, deliveredCount: 60, openedCount: 30, bouncedCount: 4, failedCount: 2,
    }})
    mockRetry.mockResolvedValue({status: 200, data: email({id: 2})})
    mockResend.mockResolvedValue({status: 200, data: email({id: 5})})
  })

  afterEach(() => {
    unmountAll(wrappers, "SentEmailsPage")
  })

  it("lists every email with its state, and counts what waits, what arrived and what needs a look", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="sent-emails-queued"]').text()).toContain("10")
    expect(wrapper.get('[data-testid="sent-emails-delivered"]').text()).toContain("90%")
    expect(wrapper.get('[data-testid="sent-emails-needs-look"]').text()).toContain("6")
    expect(wrapper.get('[data-testid="sent-email-row-1"]').text()).toContain("Job waiting")
    expect(wrapper.get('[data-testid="sent-email-status-1"]').text()).toBe("Queued")
    expect(wrapper.get('[data-testid="sent-email-row-1"]').text()).toContain("Contribution reminder")
    expect(wrapper.get('[data-testid="sent-email-open-3"]').attributes("to")).toBe("/management/mail/sent/3")
    expect(wrapper.find('[data-testid="sent-email-retry-2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="sent-email-resend-3"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="sent-email-resend-4"]').exists()).toBe(false)
  })

  it("retries and resends, re-reads the page, and says why one was refused", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="sent-email-retry-2"]').trigger("click")
    await settle()
    expect(mockRetry).toHaveBeenCalledWith({path: {id: 2}})
    await wrapper.get('[data-testid="sent-email-resend-3"]').trigger("click")
    await settle()
    expect(mockResend).toHaveBeenCalledWith({path: {id: 3}})
    expect(mockList).toHaveBeenCalledTimes(3)

    mockResend.mockResolvedValueOnce({status: 409, error: {detail: "The same email is already queued"}})
    await wrapper.get('[data-testid="sent-email-resend-3"]').trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "The same email is already queued")
  })

  it("pages, refreshes, searches, and says when there is nothing", async () => {
    const wrapper = await mount()
    await wrapper.get('[data-testid="sent-emails-next"]').trigger("click")
    await settle()
    expect(mockList).toHaveBeenLastCalledWith(expect.objectContaining({query: expect.objectContaining({page: 1})}))
    await wrapper.get('[data-testid="sent-emails-previous"]').trigger("click")
    await settle()
    await wrapper.get('[data-testid="sent-emails-refresh"]').trigger("click")
    await settle()
    expect(mockList).toHaveBeenCalledTimes(4)

    await wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "lars")
    await vi.waitFor(() => expect(mockList).toHaveBeenLastCalledWith(expect.objectContaining({query: expect.objectContaining({search: "lars"})})))

    mockList.mockResolvedValue(page([]))
    expect((await mount()).find('[data-testid="sent-emails-empty"]').exists()).toBe(true)
  })
})
