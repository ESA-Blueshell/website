import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import ExceptionDetail from "@/pages/management/ExceptionDetail.vue"
import ExceptionList from "@/pages/management/ExceptionList.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const {mockList, mockFind, mockResolve, mockStore} = vi.hoisted(() => ({
  mockList: vi.fn(),
  mockFind: vi.fn(),
  mockResolve: vi.fn(),
  mockStore: {commit: vi.fn(), getters: {isAdmin: true}},
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {...actual, useRoute: () => ({params: {id: "3"}})}
})

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {...actual, listExceptions: mockList, findException: mockFind, resolveException: mockResolve}
})

const fault = (fields: Record<string, unknown> = {}) => ({
  id: 3,
  exceptionType: "java.lang.IllegalStateException",
  thrownAt: "net.blueshell.api.contact.Sync.run",
  firstSeenAt: "2026-09-30T08:00:00Z",
  lastSeenAt: "2026-09-30T09:00:00Z",
  occurrences: 4,
  latestMessage: "Brevo said no",
  latestSource: "JOB",
  latestConcern: "contact.sync",
  latestJobExecutionId: 12,
  latestStackTrace: "IllegalStateException: Brevo said no\n\tat Sync.run(Sync.kt:4)",
  ...fields,
})

describe("the Exceptions pages", () => {
  const wrappers: VueWrapper[] = []
  const mount = async (page: typeof ExceptionList | typeof ExceptionDetail) => {
    const wrapper = mountInApp(page)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockList.mockResolvedValue({status: 200, data: [fault(), fault({id: 4, exceptionType: "java.io.IOException", latestMessage: null, resolvedAt: "2026-09-30T10:00:00Z"})]})
    mockFind.mockResolvedValue({status: 200, data: fault()})
    mockResolve.mockResolvedValue({status: 200, data: fault({resolvedAt: "2026-09-30T10:00:00Z"})})
  })

  afterEach(() => {
    unmountAll(wrappers, "Exceptions")
  })

  it("lists the open faults first, each linked to its own page", async () => {
    const wrapper = await mount(ExceptionList)

    expect(mockList).toHaveBeenCalledWith({query: {resolved: false}})
    expect(wrapper.get('[data-testid="exception-row-3"] a').attributes("to")).toBe("/management/exceptions/3")
    expect(wrapper.find('[data-testid="exception-row-3"]').text()).toContain("IllegalStateException")
    expect(wrapper.find('[data-testid="exception-row-4"]').text()).toContain("Resolved")
  })

  it("narrows by search and state, and clears both", async () => {
    const wrapper = await mount(ExceptionList)

    await wrapper.find('[data-testid="exception-search"]').setValue("ioexception")
    expect(wrapper.find('[data-testid="exception-row-3"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="exception-row-4"]').exists()).toBe(true)

    await wrapper.findComponent({name: "FilterPicker"}).vm.$emit("update:modelValue", "resolved")
    await settle()
    expect(mockList).toHaveBeenLastCalledWith({query: {resolved: true}})

    await wrapper.find('[data-testid="exception-filters-clear"]').trigger("click")
    await settle()
    expect(mockList).toHaveBeenLastCalledWith({query: {}})
    expect(wrapper.find('[data-testid="exception-row-3"]').exists()).toBe(true)
  })

  it("says so when nothing matches", async () => {
    mockList.mockResolvedValue({status: 200, data: []})
    const wrapper = await mount(ExceptionList)

    expect(wrapper.find('[data-testid="exception-list-empty"]').exists()).toBe(true)
  })

  it("opens one fault with its trace, and links the job it concerned", async () => {
    const wrapper = await mount(ExceptionDetail)

    expect(mockFind).toHaveBeenCalledWith({path: {id: 3}})
    expect(wrapper.find('[data-testid="exception-stacktrace"]').text()).toContain("Sync.kt:4")
    expect(wrapper.find('[data-testid="exception-concern-link"]').attributes("to")).toBe("/management/jobs/12")
    expect(wrapper.find('[data-testid="exception-facts"]').text()).toContain("4")
  })

  it("names a request's route without a link, and a fault without a message", async () => {
    mockFind.mockResolvedValue({status: 200, data: fault({latestSource: "REQUEST", latestConcern: "GET /events/{id}", latestJobExecutionId: null, latestMessage: null, latestStackTrace: null, resolvedAt: "2026-09-30T10:00:00Z"})})
    const wrapper = await mount(ExceptionDetail)

    expect(wrapper.find('[data-testid="exception-concern"]').text()).toBe("The request GET /events/{id}")
    expect(wrapper.find('[data-testid="exception-message"]').text()).toBe("It carried no message.")
    expect(wrapper.find('[data-testid="exception-resolve"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="exception-facts"]').text()).toContain("Resolved")
  })

  it("marks a fault resolved and reads it again", async () => {
    const wrapper = await mount(ExceptionDetail)

    await wrapper.find('[data-testid="exception-resolve"]').trigger("click")
    await settle()

    expect(mockResolve).toHaveBeenCalledWith({path: {id: 3}})
    expect(mockFind).toHaveBeenCalledTimes(2)
  })

  it("says why resolving was refused", async () => {
    mockResolve.mockResolvedValue({status: 409, error: {detail: "Nope."}})
    const wrapper = await mount(ExceptionDetail)

    await wrapper.find('[data-testid="exception-resolve"]').trigger("click")
    await settle()

    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Nope.")
    expect(mockFind).toHaveBeenCalledTimes(1)
  })

  it("says there is no such fault when it cannot be read", async () => {
    mockFind.mockResolvedValue({status: 404, error: {detail: "Not found"}})
    const wrapper = await mount(ExceptionDetail)

    expect(wrapper.find('[data-testid="exception-detail-missing"]').text()).toBe("There is no exception 3.")
  })
})
