import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import JobDetail from "@/pages/management/JobDetail.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const {mockFindJob, mockRetry, mockStore} = vi.hoisted(() => ({
  mockFindJob: vi.fn(),
  mockRetry: vi.fn(),
  mockStore: {commit: vi.fn(), getters: {isAdmin: true}},
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {...actual, useRoute: () => ({params: {id: "12"}})}
})

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {...actual, findJobById: mockFindJob, retry: mockRetry}
})

const failed = {
  id: 12,
  attempts: 3,
  jobType: "contact.sync-user",
  category: "contact",
  status: "FAILED",
  trigger: "USER_UPDATED",
  queuedAt: "2026-09-30T08:00:00Z",
  nextAttemptAt: "2026-09-30T09:00:00Z",
  errorType: "BrevoException",
  errorMessage: "Brevo said no",
  stackTrace: "BrevoException: Brevo said no\n\tat net.blueshell.api.contact.Sync.run(Sync.kt:10)",
  payload: {userId: 7, reason: "manual"},
  foldedTriggers: [],
  forced: false,
  relatedEntities: [
    {type: "USER", id: 7, label: "Ada Lovelace (@ada)"},
    {type: "CONTRIBUTION_PERIOD", id: 3, label: "2026-2027"},
  ],
}

describe("JobDetail page", () => {
  const wrappers: VueWrapper[] = []

  const mountDetail = async () => {
    const wrapper = mountInApp(JobDetail)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockFindJob.mockResolvedValue({status: 200, data: failed})
    mockRetry.mockResolvedValue({status: 200, data: {...failed, status: "QUEUED"}})
  })

  afterEach(() => {
    unmountAll(wrappers, "JobDetail")
  })

  it("shows the failure, its stack trace and the attempts", async () => {
    const wrapper = await mountDetail()

    expect(mockFindJob).toHaveBeenCalledWith({path: {id: 12}})
    expect(wrapper.find('[data-testid="job-detail-failure"]').text()).toContain("Brevo said no")
    expect(wrapper.find('[data-testid="job-detail-stacktrace"]').text()).toContain("Sync.kt:10")
    const facts = wrapper.find('[data-testid="job-detail-facts"]').text()
    expect(facts).toContain("Attempts")
    expect(facts).toContain("Next attempt")
  })

  it("links what the job concerns to its own page, and names the rest", async () => {
    const wrapper = await mountDetail()

    expect(wrapper.find('[data-testid="job-detail-concern-USER-7"]').attributes("to")).toBe("/management/users/7")
    expect(wrapper.find('[data-testid="job-detail-concerns"]').text()).toContain("2026-2027")
    expect(wrapper.find('[data-testid="job-detail-concern-CONTRIBUTION_PERIOD-3"]').exists()).toBe(false)
  })

  it("retries a failed job and reads it again", async () => {
    const wrapper = await mountDetail()

    await wrapper.find('[data-testid="job-detail-retry"]').trigger("click")
    await settle()

    expect(mockRetry).toHaveBeenCalledWith({path: {id: 12}})
    expect(mockFindJob).toHaveBeenCalledTimes(2)
  })

  it("says why a retry was refused", async () => {
    mockRetry.mockResolvedValue({status: 409, error: {detail: "That job is already running."}})
    const wrapper = await mountDetail()

    await wrapper.find('[data-testid="job-detail-retry"]').trigger("click")
    await settle()

    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "That job is already running.")
    expect(mockFindJob).toHaveBeenCalledTimes(1)
  })

  it("sends Run again to the one form that queues jobs", async () => {
    const wrapper = await mountDetail()

    expect(wrapper.find('[data-testid="job-detail-run-again"]').exists()).toBe(true)
  })

  it("says why a skipped job did nothing, and what a finished one did", async () => {
    mockFindJob.mockResolvedValue({
      status: 200,
      data: {...failed, status: "SKIPPED", trigger: null, errorType: null, errorMessage: null, stackTrace: null, skipReason: "Nothing changed.", effect: "EDITED", relatedEntities: []},
    })
    const wrapper = await mountDetail()

    expect(wrapper.find('[data-testid="job-detail-skip-reason"]').text()).toBe("Nothing changed.")
    expect(wrapper.find('[data-testid="job-detail-failure"]').exists()).toBe(false)
    expect(wrapper.text()).toContain("Nothing in particular.")
  })

  it("says a run was forced, what else queued it, and links what it did on Discord", async () => {
    mockFindJob.mockResolvedValue({status: 200, data: {
      ...failed, status: "SUCCESS", forced: true, effect: "EDITED", effectLink: "https://discord.com/channels/1/2/3", jobType: "discord.post",
      foldedTriggers: [
        {trigger: "EVENT_UPDATED", at: "2026-10-01T10:00:00Z", initiatedByType: "USER", initiatedByDisplay: "Jane Doe (@jdoe)"},
        {trigger: "EVENT_UPDATED", at: "2026-10-01T11:00:00Z", initiatedByType: "USER", initiatedByDisplay: "Jan Smit (@jsmit)"},
      ],
    }})
    const wrapper = await mountDetail()

    expect(wrapper.get('[data-testid="job-detail-forced"]').text()).toContain("Run although it would have been skipped")
    expect(wrapper.get('[data-testid="job-detail-folded-0"]').text()).toContain("Also queued by")
    expect(wrapper.get('[data-testid="job-detail-folded-0"]').text()).toContain("Jane Doe (@jdoe)")
    expect(wrapper.get('[data-testid="job-detail-folded-1"]').text()).toContain("Also queued by (2)")
    expect(wrapper.get('[data-testid="job-detail-effect-link"]').attributes("href")).toBe("https://discord.com/channels/1/2/3")
  })

  it("says there is no such job when it cannot be read", async () => {
    mockFindJob.mockResolvedValue({status: 404, error: {detail: "Not found"}})
    const wrapper = await mountDetail()

    expect(wrapper.find('[data-testid="job-detail-missing"]').text()).toBe("There is no job 12.")
  })
})
