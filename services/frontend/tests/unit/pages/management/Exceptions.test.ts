import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import ExceptionDetail from "@/pages/management/ExceptionDetail.vue"
import ExceptionList from "@/pages/management/ExceptionList.vue"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const {mockList, mockFind, mockResolve, mockStore, address} = vi.hoisted(() => ({
  mockList: vi.fn(),
  mockFind: vi.fn(),
  mockResolve: vi.fn(),
  mockStore: {commit: vi.fn(), getters: {isAdmin: true}},
  address: {route: {params: {id: "3"}}},
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  const {reactive} = await import("vue")
  address.route = reactive(address.route)
  return {...actual, useRoute: () => address.route}
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
    address.route.params = {id: "3"}
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
    expect(await sortByEveryHead(wrapper)).toBe(4)
  })

  it("draws each fault as a row on a phone, opening its own page", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = await mount(ExceptionList)
    vi.unstubAllGlobals()

    expect(wrapper.get('[data-testid="exception-row-3-open"]').attributes("to")).toBe("/management/exceptions/3")
    expect(wrapper.get('[data-testid="exception-row-4"]').text()).toContain("Resolved")
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
    expect(wrapper.find('[data-testid="exception-list-unread"]').exists()).toBe(false)
  })

  it("says the list could not be read when the read fails, never that there are none, and reads again when asked", async () => {
    mockList.mockResolvedValue({status: 500, error: {}})
    const wrapper = await mount(ExceptionList)

    expect(wrapper.find('[data-testid="exception-list-unread"]').text()).toContain("The exceptions could not be read.")
    expect(wrapper.find('[data-testid="exception-list-empty"]').exists()).toBe(false)

    mockList.mockResolvedValue({status: 200, data: [fault()]})
    await wrapper.find('[data-testid="exception-list-reload"]').trigger("click")
    await settle()

    expect(wrapper.find('[data-testid="exception-row-3"]').exists()).toBe(true)
  })

  it("shows the state picked last, whichever read answers last", async () => {
    const wrapper = await mount(ExceptionList)
    const answers: Array<(read: unknown) => void> = []
    mockList.mockImplementation(() => new Promise((resolve) => answers.push(resolve)))

    await wrapper.findComponent({name: "FilterPicker"}).vm.$emit("update:modelValue", "resolved")
    await wrapper.findComponent({name: "FilterPicker"}).vm.$emit("update:modelValue", null)
    answers[1]({status: 200, data: [fault()]})
    await settle()
    answers[0]({status: 200, data: [fault({id: 4, resolvedAt: "2026-09-30T10:00:00Z"})]})
    await settle()

    expect(wrapper.find('[data-testid="exception-row-3"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="exception-row-4"]').exists()).toBe(false)
  })

  it("puts the fault seen last first under Last seen, whatever fraction its moment carries", async () => {
    mockList.mockResolvedValue({status: 200, data: [
      fault({id: 3, lastSeenAt: "2026-09-30T09:00:00Z"}),
      fault({id: 4, lastSeenAt: "2026-09-30T09:00:00.500Z"}),
      fault({id: 5, lastSeenAt: "2026-09-30T09:00:01Z"}),
    ]})
    const wrapper = await mount(ExceptionList)

    await wrapper.get('[data-testid="exception-table-sort-seen"]').trigger("click")

    expect(wrapper.findAll("tr[data-row]").map((row) => row.attributes("data-testid"))).toEqual(["exception-row-5", "exception-row-4", "exception-row-3"])
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

  it("marks a fault resolved and shows it as it then stands", async () => {
    const wrapper = await mount(ExceptionDetail)
    mockFind.mockResolvedValue({status: 200, data: fault({resolvedAt: "2026-09-30T10:00:00Z"})})

    await wrapper.find('[data-testid="exception-resolve"]').trigger("click")
    await settle()

    expect(mockResolve).toHaveBeenCalledWith({path: {id: 3}})
    expect(wrapper.find('[data-testid="exception-resolve"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="exception-facts"]').text()).toContain("Resolved")
  })

  it("shows the fault the address names last, whichever read answers last", async () => {
    const answers: Array<(read: unknown) => void> = []
    mockFind.mockImplementation(() => new Promise((resolve) => answers.push(resolve)))
    const wrapper = await mount(ExceptionDetail)

    address.route.params = {id: "4"}
    await settle()
    answers[1]({status: 200, data: fault({id: 4, exceptionType: "java.io.IOException"})})
    await settle()
    answers[0]({status: 200, data: fault()})
    await settle()

    expect(wrapper.text()).toContain("java.io.IOException")
  })

  it("says why resolving was refused", async () => {
    mockResolve.mockResolvedValue({status: 409, error: {detail: "Nope."}})
    const wrapper = await mount(ExceptionDetail)

    await wrapper.find('[data-testid="exception-resolve"]').trigger("click")
    await settle()

    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Nope.")
    expect(mockFind).toHaveBeenCalledTimes(1)
  })

  it("says there is no such fault when the api has none", async () => {
    mockFind.mockResolvedValue({status: 404, error: {detail: "Not found"}})
    const wrapper = await mount(ExceptionDetail)

    expect(wrapper.find('[data-testid="exception-detail-missing"]').text()).toBe("There is no exception 3.")
    expect(wrapper.find('[data-testid="exception-detail-unread"]').exists()).toBe(false)
  })

  it("says a fault could not be read when the read fails, and reads it again when asked", async () => {
    mockFind.mockResolvedValue({status: 500, error: {}})
    const wrapper = await mount(ExceptionDetail)

    expect(wrapper.find('[data-testid="exception-detail-unread"]').text()).toBe("Exception 3 could not be read.")
    expect(wrapper.find('[data-testid="exception-detail-missing"]').exists()).toBe(false)

    mockFind.mockResolvedValue({status: 200, data: fault()})
    await wrapper.find('[data-testid="exception-detail-reload"]').trigger("click")
    await settle()

    expect(wrapper.find('[data-testid="exception-stacktrace"]').text()).toContain("Sync.kt:4")
  })

  it("reads the other fault when the address names another", async () => {
    await mount(ExceptionDetail)

    address.route.params = {id: "4"}
    await settle()

    expect(mockFind).toHaveBeenLastCalledWith({path: {id: 4}})
  })
})
