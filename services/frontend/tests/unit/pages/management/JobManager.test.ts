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

const {mockRouterReplace, mockList, mockRetry, mockGetStats, mockJobTypes, mockFindJob, mockStore, mockRoute} = vi.hoisted(() => ({
  mockRouterReplace: vi.fn(),
  mockList: vi.fn(),
  mockRetry: vi.fn(),
  mockGetStats: vi.fn(),
  mockJobTypes: vi.fn(),
  mockFindJob: vi.fn(),
  mockStore: {commit: vi.fn(), getters: {isAdmin: true}},
  mockRoute: {query: {} as Record<string, string>},
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {...actual, useRouter: () => ({replace: mockRouterReplace}), useRoute: () => mockRoute}
})

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => {
  // The real generated enums stay, so the filter options are the api's; only the calls are stubbed.
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {...actual, list: mockList, retry: mockRetry, getStats: mockGetStats, jobTypes: mockJobTypes, findJobById: mockFindJob}
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
    mockRoute.query = {}
    mockJobTypes.mockResolvedValue({status: 200, data: [{type: "contact.sync-user", payloadFields: [{name: "userId", type: "Long", required: true}]}]})
    mockList.mockResolvedValue(pageOf([job({id: 1, status: "FAILED"}), job({id: 2})], 2))
    mockRetry.mockResolvedValue({status: 200, data: job({id: 1, attempts: 2})})
    // Every field, because the stats panel calls toFixed on four of them and a partial object
    // renders as a TypeError rather than a blank.
    mockGetStats.mockResolvedValue({
      status: 200,
      data: {
        avgSuccessDurationSeconds: 1.5, deadCount: 0, deadSinceStartup: 0, failedCount: 1,
        failedSinceStartup: 1, queuedCount: 0, recoveriesSinceStartup: 0, runningCount: 0,
        skippedCount: 0, successCount: 1, totalCount: 2,
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

  it("draws each job as a row on a phone, opening the job's own page", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = mountJobManager()
    await settle()
    vi.unstubAllGlobals()

    expect(wrapper.get('[data-testid="job-row-1-open"]').attributes("to")).toBe("/management/jobs/1")
    expect(wrapper.findAllComponents({name: "ManagementRow"})[0]!.findComponent({name: "StateMark"}).exists()).toBe(true)
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

  it("says why a skipped run did nothing, that a forced one was forced, and offers to run it anyway", async () => {
    mockList.mockResolvedValue(pageOf([
      job({id: 3, status: "SKIPPED", forced: true, skipReason: "The event is over."}),
    ]))

    const wrapper = mountJobManager()
    await settle()

    expect(wrapper.find('[data-testid="job-retry-btn-3"]').text()).toBe("Run anyway")
    expect(wrapper.find('[data-testid="job-row-3"]').text()).toContain("Skipped")
    expect(wrapper.find('[data-testid="job-skip-reason-3"]').text()).toBe("The event is over.")
  })

  it("says what queued each job on its row, or who did", async () => {
    mockList.mockResolvedValue(pageOf([job({id: 4, trigger: "SIGN_UPS_CHANGED"}), job({id: 5})]))

    const wrapper = mountJobManager()
    await settle()

    expect(wrapper.find('[data-testid="job-row-trigger-4"]').text()).toBe("A change in sign-ups")
    expect(wrapper.find('[data-testid="job-row-trigger-5"]').text()).not.toBe("")
  })

  it("says what a run did, and hides skipped runs on request", async () => {
    mockList.mockResolvedValue(pageOf([
      job({id: 6, jobType: "discord.post", effect: "EDITED", effectLink: "https://discord.com/channels/1/2/3"}),
      job({id: 7}),
    ]))

    const wrapper = mountJobManager()
    await settle()

    expect(wrapper.find('[data-testid="job-row-effect-6"]').text()).toBe("Edited the #events-calendar post")
    expect(wrapper.find('[data-testid="job-row-effect-7"]').exists()).toBe(false)

    mockList.mockClear()
    const skipped = () => wrapper.findAllComponents({name: "FilterPicker"}).find((one) => one.props("testid") === "job-filter-hide-skipped")!
    expect(skipped().props("modelValue")).toBeNull()
    skipped().vm.$emit("update:modelValue", "hidden")
    await settle()
    expect(mockList).toHaveBeenLastCalledWith({query: expect.objectContaining({page: 0, hideSkipped: true})})
    expect(skipped().props("modelValue")).toBe("hidden")
    skipped().vm.$emit("update:modelValue", null)
    await settle()
    expect(mockList).toHaveBeenLastCalledWith({query: expect.not.objectContaining({hideSkipped: true})})
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

    await wrapper.find('[data-testid="job-filters-clear"]').trigger("click")
    await settle()

    const query = mockList.mock.lastCall?.[0]?.query as Record<string, unknown>
    expect(query.category).toBeUndefined()
    expect(query.status).toBeUndefined()
    expect(wrapper.find('[data-testid="job-filters-clear"]').exists()).toBe(false)
  })

  it("reads Status and Kind off their pickers", async () => {
    const wrapper = mountJobManager()
    await settle()

    const [status, kind] = wrapper.findAllComponents({name: "FilterPicker"})
    await status.vm.$emit("update:modelValue", "DEAD")
    await kind.vm.$emit("update:modelValue", "email")
    await settle()

    expect(mockList).toHaveBeenLastCalledWith({query: expect.objectContaining({status: "DEAD", category: "email"})})
  })

  it("searches jobs alongside the pickers", async () => {
    const wrapper = mountJobManager()
    await settle()

    const vm = wrapper.vm as any
    vm.selectedStatus = "FAILED"
    await wrapper.find('[data-testid="job-filter-search"]').setValue("sitecie")
    await new Promise((resolve) => setTimeout(resolve, 400))
    await settle()

    expect(mockList).toHaveBeenLastCalledWith({
      query: expect.objectContaining({status: "FAILED", search: "sitecie"}),
    })
  })

  it("fills Run a job from a row's Run again, and queues nothing by itself", async () => {
    mockList.mockResolvedValue(pageOf([job({id: 1, status: "FAILED", payload: {userId: 7}})]))
    const wrapper = mountJobManager()
    await settle()

    expect(wrapper.find('[data-testid="job-run-form"]').exists()).toBe(false)
    await wrapper.find('[data-testid="job-run-again-btn-1"]').trigger("click")
    await settle()

    expect(wrapper.find('[data-testid="job-run-form"]').exists()).toBe(true)
    expect((wrapper.vm as any).preset).toEqual({type: "contact.sync-user", payload: {userId: 7}})
    expect(mockList).toHaveBeenCalledTimes(1)
  })

  it("opens Run a job filled in when a job's own page sends Run again", async () => {
    mockRoute.query = {again: "4"}
    mockFindJob.mockResolvedValue({status: 200, data: job({id: 4, payload: {userId: 9}})})

    const wrapper = mountJobManager()
    await settle()

    expect(mockFindJob).toHaveBeenCalledWith({path: {id: 4}})
    expect((wrapper.vm as any).runOpen).toBe(true)
    expect((wrapper.vm as any).preset).toEqual({type: "contact.sync-user", payload: {userId: 9}})
  })

  it("ignores a Run again for a job it cannot read, or one without a type", async () => {
    mockRoute.query = {again: "4"}
    mockFindJob.mockResolvedValue({status: 404, error: {detail: "Not found"}})

    const wrapper = mountJobManager()
    await settle()
    expect((wrapper.vm as any).runOpen).toBe(false)

    await (wrapper.vm as any).runAgain({jobType: null, payload: {}})
    expect((wrapper.vm as any).runOpen).toBe(false)
  })

  it("goes back to the first page once a job is queued", async () => {
    const wrapper = mountJobManager()
    await settle()
    mockList.mockClear()

    await wrapper.find('[data-testid="job-run-toggle"]').trigger("click")
    await settle()
    wrapper.findComponent({name: "JobRunForm"}).vm.$emit("queued", "contact.sync-user")
    await settle()

    expect(mockList).toHaveBeenLastCalledWith({query: expect.objectContaining({page: 0})})
  })

  it("opens filtered on the status an alert linked it with", async () => {
    mockRoute.query = {status: "DEAD"}
    mountJobManager()
    await settle()

    expect(mockList).toHaveBeenLastCalledWith({query: expect.objectContaining({status: "DEAD"})})
  })

  it("links each row to the job's own page, and reads more as the list is scrolled", async () => {
    mockList.mockResolvedValue({status: 200, data: {content: [job({id: 1})], page: {number: 0, size: 50, totalElements: 120, totalPages: 3}}})
    const wrapper = mountJobManager()
    await settle()

    wrapper.findComponent({name: "ManagementTable"}).vm.$emit("more")
    await settle()
    expect(mockList).toHaveBeenLastCalledWith({query: expect.objectContaining({page: 1})})
    await wrapper.get('[data-testid="job-run-open"]').trigger("click")
    expect(wrapper.findComponent({name: "FoldOut"}).props("open")).toBe(true)

    expect(wrapper.find('[data-testid="job-open-1"]').attributes("to")).toBe("/management/jobs/1")
  })

  it("shows an empty table rather than stale rows when the read is refused", async () => {
    mockList.mockResolvedValue({status: 403, error: {detail: "Forbidden"}})

    const wrapper = mountJobManager()
    await settle()

    expect(wrapper.find('[data-testid="job-row-1"]').exists()).toBe(false)
    expect(wrapper.text()).toContain("No jobs match.")
  })

  it("reads the older list shape, in which the api answers with a bare array", async () => {
    mockList.mockResolvedValue({
      status: 200,
      data: [job({id: 1}), job({id: 2}), job({id: 3})],
    })

    const wrapper = mountJobManager()
    await settle()

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
