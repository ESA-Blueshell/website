/**
 * What the email manager does that only a browser could reach. It had no unit test at all: the
 * only thing standing behind it was an e2e spec that drove a browser and asserted none of the
 * signals below.
 *
 * The reading rules live in `domains/emails` and are checked there, without a mount.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import EmailManager from "@/pages/management/EmailManager.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const {mockList, mockRetry, mockGetStats, mockPreview, mockStore} = vi.hoisted(() => ({
  mockList: vi.fn(),
  mockRetry: vi.fn(),
  mockGetStats: vi.fn(),
  mockPreview: vi.fn(),
  mockStore: {commit: vi.fn(), getters: {}},
}))

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => {
  // The real generated enums stay, so the status filter is the api's; only the calls are stubbed.
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    list1: mockList,
    retry1: mockRetry,
    getStats1: mockGetStats,
    previewSentEmail: mockPreview,
  }
})

const email = (fields: Record<string, unknown>) => ({
  attempts: 1,
  createdAt: "2026-01-15T10:30:00Z",
  deliveryStatus: "SENT",
  emailType: "recovery",
  previewable: false,
  recipientEmail: "member@example.com",
  subject: "Welcome",
  ...fields,
})

const pageOf = (content: unknown[], totalElements = content.length, totalPages = 1) => ({
  status: 200,
  data: {content, page: {number: 0, size: 50, totalElements, totalPages}},
})

describe("EmailManager page", () => {
  const wrappers: VueWrapper[] = []

  const mountEmailManager = () => {
    const wrapper = mountInApp(EmailManager, {
      global: {stubs: {RouterLink: {template: "<a><slot /></a>"}}},
    })
    wrappers.push(wrapper)
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockList.mockResolvedValue(pageOf([
      email({id: 1, deliveryStatus: "FAILED", jobExecutionId: 9}),
      email({id: 2, deliveryStatus: "OPENED", previewable: true}),
    ], 2))
    mockRetry.mockResolvedValue({status: 200, data: email({id: 1, deliveryStatus: "PENDING"})})
    mockGetStats.mockResolvedValue({
      status: 200,
      data: {
        bouncedCount: 0, deliveredCount: 4, failedCount: 1, openedCount: 3, pendingCount: 0,
        sentCount: 2, totalCount: 10,
      },
    })
    mockPreview.mockResolvedValue({status: 200, data: {subject: "Welcome", html: "<p>hi</p>"}})
  })

  afterEach(() => {
    unmountAll(wrappers, "EmailManager")
  })

  it("draws a row for every email the api answered with", async () => {
    const wrapper = mountEmailManager()
    await settle()

    expect(mockList).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-testid="email-row-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="email-row-2"]').exists()).toBe(true)
  })

  it("shows the rates the stats endpoint implies", async () => {
    const wrapper = mountEmailManager()
    await settle()

    // Four delivered plus three opened out of ten, and three of ten opened.
    expect(wrapper.find('[data-testid="email-stats-delivered"]').text()).toContain("70%")
    expect(wrapper.find('[data-testid="email-stats-opened"]').text()).toContain("30%")
  })

  it("opens a row into its detail and closes it again", async () => {
    const wrapper = mountEmailManager()
    await settle()

    expect(wrapper.find('[data-testid="email-detail-1"]').exists()).toBe(false)

    await wrapper.find('[data-testid="email-row-1"]').trigger("click")
    await settle()
    expect(wrapper.find('[data-testid="email-detail-1"]').exists()).toBe(true)

    await wrapper.find('[data-testid="email-row-1"]').trigger("click")
    await settle()
    // Read off the state rather than the dom: the collapse is a transition, so the panel is
    // still mounted at this point on its way out.
    expect((wrapper.vm as any).isExpanded({id: 1})).toBe(false)
  })

  it("offers Retry only for a failed send with a job behind it, and Preview only where stored", async () => {
    const wrapper = mountEmailManager()
    await settle()

    expect(wrapper.find('[data-testid="email-retry-btn-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="email-retry-btn-2"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="email-preview-btn-2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="email-preview-btn-1"]').exists()).toBe(false)
  })

  it("re-reads the page after a retry lands", async () => {
    const wrapper = mountEmailManager()
    await settle()

    await wrapper.find('[data-testid="email-retry-btn-1"]').trigger("click")
    await settle()

    expect(mockRetry).toHaveBeenCalledWith({path: {id: 1}})
    expect(mockList).toHaveBeenCalledTimes(2)
  })

  // Used to fall into an empty catch, so a refused retry looked exactly like a successful one.
  it("says why a retry was refused, in the api's own words", async () => {
    mockRetry.mockResolvedValueOnce({status: 409, error: {detail: "That email is already queued."}})

    const wrapper = mountEmailManager()
    await settle()

    await wrapper.find('[data-testid="email-retry-btn-1"]').trigger("click")
    await settle()

    expect(mockStore.commit)
      .toHaveBeenCalledWith("setStatusSnackbarMessage", "That email is already queued.")
    expect(mockList).toHaveBeenCalledTimes(1)
  })

  it("falls back to its own sentence when a refusal carries no words", async () => {
    mockRetry.mockResolvedValueOnce({status: 500, error: {}})

    const wrapper = mountEmailManager()
    await settle()

    await wrapper.find('[data-testid="email-retry-btn-1"]').trigger("click")
    await settle()

    expect(mockStore.commit).toHaveBeenCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("could not be sent again"),
    )
  })

  it("carries the chosen status into the query, and drops it when cleared", async () => {
    const wrapper = mountEmailManager()
    await settle()
    mockList.mockClear()

    const vm = wrapper.vm as any
    vm.selectedStatus = "FAILED"
    await settle()

    expect(mockList).toHaveBeenLastCalledWith({
      query: expect.objectContaining({page: 0, size: 50, deliveryStatus: "FAILED"}),
    })

    vm.selectedStatus = "all"
    await settle()

    const query = mockList.mock.lastCall?.[0]?.query as Record<string, unknown>
    expect(query.deliveryStatus).toBeUndefined()
  })

  it("shows an empty table rather than stale rows when the read is refused", async () => {
    mockList.mockResolvedValue({status: 403, error: {detail: "Forbidden"}})

    const wrapper = mountEmailManager()
    await settle()

    expect(wrapper.find('[data-testid="email-row-1"]').exists()).toBe(false)
    expect(wrapper.text()).toContain("No emails found.")
  })

  it("keeps the stats panel out of the way when the counts cannot be read", async () => {
    mockGetStats.mockResolvedValue({status: 500, error: {detail: "nope"}})

    const wrapper = mountEmailManager()
    await settle()

    expect(wrapper.find('[data-testid="email-stats-total"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="email-row-1"]').exists()).toBe(true)
  })

  it("renders a stored email back through the preview dialog", async () => {
    const wrapper = mountEmailManager()
    await settle()

    await wrapper.find('[data-testid="email-preview-btn-2"]').trigger("click")
    await settle()

    expect(mockPreview).toHaveBeenCalledWith({path: {id: 2}})
    expect((wrapper.vm as any).preview.subject).toBe("Welcome")
  })
})
