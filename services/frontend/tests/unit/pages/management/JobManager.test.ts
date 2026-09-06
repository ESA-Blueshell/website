/**
 * What the job manager does that only a browser could reach: the route guard, the query its
 * filters build, and — the reason this file mounts rather than calls — what a rendered row
 * actually puts on screen out of a job's payload.
 *
 * The rules themselves live in `domains/jobs` and are checked there, without a mount.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import JobManager from "@/pages/management/JobManager.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const {mockRouterReplace, mockList, mockRetry, mockGetStats, mockStore} = vi.hoisted(() => ({
  mockRouterReplace: vi.fn(),
  mockList: vi.fn(),
  mockRetry: vi.fn(),
  mockGetStats: vi.fn(),
  mockStore: {commit: vi.fn(), getters: {isAdmin: true}},
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {...actual, useRouter: () => ({replace: mockRouterReplace})}
})

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => {
  // The real generated enums stay, so the filter options are the api's; only the calls are stubbed.
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {...actual, list: mockList, retry: mockRetry, getStats: mockGetStats}
})

const job = (fields: Record<string, unknown>) => ({
  attempts: 1,
  jobType: "contact.sync-user",
  relatedEntities: [],
  status: "SUCCESS",
  ...fields,
})

const pageOf = (content: unknown[], totalElements = content.length, totalPages = 1) => ({
  status: 200,
  data: {content, page: {number: 0, size: 50, totalElements, totalPages}},
})

describe("JobManager page", () => {
  const wrappers: VueWrapper[] = []

  const mountJobManager = () => {
    const wrapper = mountInApp(JobManager)
    wrappers.push(wrapper)
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isAdmin = true
    mockList.mockResolvedValue(pageOf([job({id: 1, status: "FAILED"}), job({id: 2})], 2))
    mockRetry.mockResolvedValue({status: 200, data: job({id: 1, attempts: 2})})
    // Every field, because the stats panel calls toFixed on four of them and a partial object
    // renders as a TypeError rather than a blank.
    mockGetStats.mockResolvedValue({
      status: 200,
      data: {
        avgSuccessDurationSeconds: 1.5, deadCount: 0, deadSinceStartup: 0, failedCount: 1,
        failedSinceStartup: 1, queuedCount: 0, recoveriesSinceStartup: 0, runningCount: 0,
        successCount: 1, totalCount: 2,
      },
    })
  })

  afterEach(() => {
    unmountAll(wrappers, "JobManager")
  })

  it("sends a non-admin away without reading anything", async () => {
    mockStore.getters.isAdmin = false

    mountJobManager()
    await settle()

    expect(mockRouterReplace).toHaveBeenCalledWith("/")
    expect(mockList).not.toHaveBeenCalled()
  })

  it("draws a row for every job the api answered with", async () => {
    const wrapper = mountJobManager()
    await settle()

    expect(mockList).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-testid="job-row-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="job-row-2"]').exists()).toBe(true)
  })

  // The redaction rule is unit-tested in domains/jobs; this proves the page is wired to it, which
  // is the half that a rule nobody calls would still pass.
  it("puts a payload's readable fields on the row and its secrets nowhere", async () => {
    mockList.mockResolvedValue(pageOf([
      job({id: 1, payload: {reason: "manual", discordToken: "tok_live_secret", userId: 7}}),
    ]))

    const wrapper = mountJobManager()
    await settle()

    const chips = wrapper.find('[data-testid="job-row-payload-1"]')
    expect(chips.text()).toContain("Reason")
    expect(chips.text()).toContain("manual")
    expect(wrapper.html()).not.toContain("tok_live_secret")
    // Already shown as a resolved related entity, so not repeated as a raw id.
    expect(chips.text()).not.toContain("User Id")
  })

  it("opens a row into its detail and closes it again", async () => {
    const wrapper = mountJobManager()
    await settle()

    expect(wrapper.find('[data-testid="job-detail-1"]').exists()).toBe(false)

    await wrapper.find('[data-testid="job-row-1"]').trigger("click")
    await settle()
    expect(wrapper.find('[data-testid="job-detail-1"]').exists()).toBe(true)

    await wrapper.find('[data-testid="job-row-1"]').trigger("click")
    await settle()
    // Read off the state rather than the dom: the collapse is a transition, so the panel is
    // still mounted at this point on its way out.
    expect((wrapper.vm as any).isExpanded({id: 1})).toBe(false)
  })

  it("offers Retry only on a job that stopped without succeeding", async () => {
    const wrapper = mountJobManager()
    await settle()

    expect(wrapper.find('[data-testid="job-retry-btn-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="job-retry-btn-2"]').exists()).toBe(false)
  })

  it("re-reads the page after a retry lands", async () => {
    const wrapper = mountJobManager()
    await settle()

    await wrapper.find('[data-testid="job-retry-btn-1"]').trigger("click")
    await settle()

    expect(mockRetry).toHaveBeenCalledWith({path: {id: 1}})
    expect(mockList).toHaveBeenCalledTimes(2)
  })

  // Pressing Retry and being told nothing is indistinguishable from pressing nothing at all.
  it("says why a retry was refused, in the api's own words", async () => {
    mockRetry.mockResolvedValueOnce({status: 409, error: {detail: "That job is already running."}})

    const wrapper = mountJobManager()
    await settle()

    await wrapper.find('[data-testid="job-retry-btn-1"]').trigger("click")
    await settle()

    expect(mockStore.commit)
      .toHaveBeenCalledWith("setStatusSnackbarMessage", "That job is already running.")
    // A refused retry changed nothing, so there is nothing to re-read.
    expect(mockList).toHaveBeenCalledTimes(1)
  })

  it("falls back to its own sentence when a refusal carries no words", async () => {
    mockRetry.mockResolvedValueOnce({status: 500, error: {}})

    const wrapper = mountJobManager()
    await settle()

    await wrapper.find('[data-testid="job-retry-btn-1"]').trigger("click")
    await settle()

    expect(mockStore.commit).toHaveBeenCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("could not be retried"),
    )
  })

  it("carries the chosen filters into the query, and drops them when cleared", async () => {
    const wrapper = mountJobManager()
    await settle()
    mockList.mockClear()

    const vm = wrapper.vm as any
    vm.selectedCategory = "calendar"
    vm.selectedStatus = "FAILED"
    await settle()

    expect(mockList).toHaveBeenLastCalledWith({
      query: expect.objectContaining({page: 0, size: 50, category: "calendar", status: "FAILED"}),
    })

    vm.selectedCategory = "all"
    vm.selectedStatus = "all"
    await settle()

    const query = mockList.mock.lastCall?.[0]?.query as Record<string, unknown>
    expect(query.category).toBeUndefined()
    expect(query.status).toBeUndefined()
  })

  it("shows an empty table rather than stale rows when the read is refused", async () => {
    mockList.mockResolvedValue({status: 403, error: {detail: "Forbidden"}})

    const wrapper = mountJobManager()
    await settle()

    expect(wrapper.find('[data-testid="job-row-1"]').exists()).toBe(false)
    expect(wrapper.text()).toContain("No job executions found.")
  })

  it("reads the older list shape, in which the api answers with a bare array", async () => {
    mockList.mockResolvedValue({
      status: 200,
      data: [job({id: 1}), job({id: 2}), job({id: 3})],
    })

    const wrapper = mountJobManager()
    await settle()

    expect((wrapper.vm as any).totalPages).toBe(1)
    expect(wrapper.find('[data-testid="job-row-3"]').exists()).toBe(true)
  })

  it("keeps the stats panel out of the way when the counts cannot be read", async () => {
    mockGetStats.mockResolvedValue({status: 500, error: {detail: "nope"}})

    const wrapper = mountJobManager()
    await settle()

    expect(wrapper.find('[data-testid="job-stats-total"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="job-row-1"]').exists()).toBe(true)
  })
})
