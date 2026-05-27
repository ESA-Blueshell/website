import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {type VueWrapper, shallowMount} from "@vue/test-utils"
import JobManager from "@/pages/management/JobManager.vue"
import {settle} from "../helpers"

const {
  mockRouterReplace,
  mockList,
  mockRetry,
  mockHandleNetworkError,
  mockStore,
} = vi.hoisted(() => ({
  mockRouterReplace: vi.fn(),
  mockList: vi.fn(),
  mockRetry: vi.fn(),
  mockHandleNetworkError: vi.fn(),
  mockStore: {
    getters: {
      isAdmin: true,
    },
  },
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRouter: () => ({
      replace: mockRouterReplace,
    }),
  }
})

vi.mock("@/plugins/store", () => ({
  default: mockStore,
}))

vi.mock("@/services/api", async (importOriginal) => {
  // Keep the real generated enums (JobExecutionCategory, JobExecutionStatus, …)
  // so the page's filter options stay in sync with the API; stub only the calls.
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    list: mockList,
    retry: mockRetry,
  }
})

vi.mock("@/plugins/handleNetworkError", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("JobManager page", () => {
  const wrappers: VueWrapper[] = []

  const mountJobManager = () => {
    const wrapper = shallowMount(JobManager)
    wrappers.push(wrapper)
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isAdmin = true
    mockList.mockResolvedValue({
      status: 200,
      data: {
        content: [
          {id: 1, status: "FAILED", jobType: "SYNC", attempts: 1},
          {id: 2, status: "SUCCESS", jobType: "SYNC", attempts: 1},
        ],
        page: {number: 0, size: 50, totalElements: 2, totalPages: 1},
      },
    })
    mockRetry.mockResolvedValue({
      status: 200,
      data: {id: 1, status: "SUCCESS", jobType: "SYNC", attempts: 2},
    })
  })

  afterEach(() => {
    while (wrappers.length > 0) {
      wrappers.pop()?.unmount()
    }
  })

  it("loads and retries jobs for admins", async () => {
    mockList
      .mockResolvedValueOnce({
        status: 200,
        data: {
          content: [
            {id: 1, status: "FAILED", jobType: "SYNC", attempts: 1},
            {id: 2, status: "SUCCESS", jobType: "SYNC", attempts: 1},
          ],
          page: {number: 0, size: 50, totalElements: 2, totalPages: 1},
        },
      })
      .mockResolvedValueOnce({
        status: 200,
        data: {
          content: [
            {id: 1, status: "SUCCESS", jobType: "SYNC", attempts: 2},
            {id: 2, status: "SUCCESS", jobType: "SYNC", attempts: 1},
          ],
          page: {number: 0, size: 50, totalElements: 2, totalPages: 1},
        },
      })

    const wrapper = mountJobManager()
    await settle()

    expect(mockList).toHaveBeenCalledTimes(1)
    expect(mockList).toHaveBeenCalledWith(expect.objectContaining({
      query: expect.objectContaining({
        page: 0,
        size: 50,
      }),
    }))
    expect((wrapper.vm as any).executions).toHaveLength(2)

    await (wrapper.vm as any).retry({id: 1})
    await settle()
    expect(mockRetry).toHaveBeenCalledWith({path: {id: 1}})
    expect(mockList).toHaveBeenCalledTimes(2)
    expect((wrapper.vm as any).executions.find((j: { id: number }) => j.id === 1)?.status).toBe("SUCCESS")
  })

  it("applies selected filters as backend query params", async () => {
    const wrapper = mountJobManager()
    await settle()
    mockList.mockClear()

    ;(wrapper.vm as any).selectedCategory = "calendar"
    ;(wrapper.vm as any).selectedStatus = "FAILED"
    await settle()

    expect(mockList).toHaveBeenCalled()
    expect(mockList).toHaveBeenLastCalledWith(expect.objectContaining({
      query: expect.objectContaining({
        page: 0,
        size: 50,
        category: "calendar",
        status: "FAILED",
      }),
    }))
  })

  it("shows all category options from enum, not from current page rows", async () => {
    const wrapper = mountJobManager()
    await settle()

    expect((wrapper.vm as any).categoryOptions).toEqual([
      {title: "All categories", value: "all"},
      {title: "Calendar", value: "calendar"},
      {title: "Contact", value: "contact"},
      {title: "Email", value: "email"},
      {title: "Other", value: "other"},
    ])
  })

  it("handles cleared search filter value without trim error", async () => {
    const wrapper = mountJobManager()
    await settle()
    mockList.mockClear()
    mockHandleNetworkError.mockClear()

    ;(wrapper.vm as any).searchQuery = null
    await (wrapper.vm as any).refresh()
    await settle()

    expect(mockHandleNetworkError).not.toHaveBeenCalled()
    const lastQuery = mockList.mock.lastCall?.[0]?.query as {search?: string} | undefined
    expect(lastQuery).toBeDefined()
    expect(lastQuery?.search).toBeUndefined()
  })

  it("paginates jobs with 50 entries per page from backend", async () => {
    const jobs = Array.from({length: 51}, (_, index) => ({
      id: index + 1,
      status: "QUEUED",
      jobType: `job.${index + 1}`,
      attempts: 0,
      category: "contact",
    }))

    mockList.mockImplementation(({query}: {query?: {page?: number, size?: number}}) => {
      const page = query?.page ?? 0
      const size = query?.size ?? 50
      const start = page * size
      const content = jobs.slice(start, start + size)
      return Promise.resolve({
        status: 200,
        data: {
          content,
          page: {
            number: page,
            size,
            totalElements: jobs.length,
            totalPages: 2,
          },
        },
      })
    })

    const wrapper = mountJobManager()
    await settle()

    expect((wrapper.vm as any).totalPages).toBe(2)
    expect((wrapper.vm as any).executions).toHaveLength(50)

    ;(wrapper.vm as any).page = 2
    await settle()
    expect((wrapper.vm as any).executions).toHaveLength(1)
    expect(mockList).toHaveBeenLastCalledWith(expect.objectContaining({
      query: expect.objectContaining({
        page: 1,
        size: 50,
      }),
    }))
  })

  it("supports legacy list payload shape", async () => {
    mockList.mockResolvedValue({
      status: 200,
      data: Array.from({length: 3}, (_, index) => ({
        id: index + 1,
        status: "QUEUED",
        jobType: `job.${index + 1}`,
        attempts: 0,
        category: "contact",
      })),
    })

    const wrapper = mountJobManager()
    await settle()

    expect((wrapper.vm as any).totalPages).toBe(1)
    expect((wrapper.vm as any).executions).toHaveLength(3)
  })

  it("redirects non-admins", async () => {
    mockStore.getters.isAdmin = false

    mountJobManager()
    await settle()

    expect(mockRouterReplace).toHaveBeenCalledWith("/")
    expect(mockList).not.toHaveBeenCalled()
  })

  it("statusColor returns correct Vuetify color for each status", async () => {
    const wrapper = mountJobManager()
    await settle()

    const vm = wrapper.vm as any
    expect(vm.statusColor("SUCCESS")).toBe("success")
    expect(vm.statusColor("FAILED")).toBe("error")
    expect(vm.statusColor("RUNNING")).toBe("info")
    expect(vm.statusColor("QUEUED")).toBe("warning")
    expect(vm.statusColor("UNKNOWN")).toBe("secondary")
    expect(vm.statusColor(undefined)).toBe("secondary")
  })

  it("actorDisplay formats actor names correctly", async () => {
    const wrapper = mountJobManager()
    await settle()

    const vm = wrapper.vm as any

    expect(vm.actorDisplay({initiatedByDisplay: "Admin User"})).toBe("Admin User")
    expect(vm.actorDisplay({
      initiatedByFullName: "John Doe",
      initiatedByUsername: "jdoe",
    })).toBe("John Doe (@jdoe)")
    expect(vm.actorDisplay({initiatedByType: "SYSTEM"})).toBe("System")
    expect(vm.actorDisplay({initiatedByUserId: 42})).toBe("User #42")
    expect(vm.actorDisplay({})).toBe("System")
  })

  it("previewActorDisplay formats preview actor names correctly", async () => {
    const wrapper = mountJobManager()
    await settle()

    const vm = wrapper.vm as any

    expect(vm.previewActorDisplay({initiatedByFullName: "Jane Doe"})).toBe("Jane Doe")
    expect(vm.previewActorDisplay({initiatedByDisplay: "Bob (@bob)"})).toBe("Bob")
    expect(vm.previewActorDisplay({initiatedByType: "SYSTEM"})).toBe("System")
    expect(vm.previewActorDisplay({initiatedByUserId: 7})).toBe("User #7")
    expect(vm.previewActorDisplay({})).toBe("System")
  })

  it("formatDate formats timestamps and returns dash for falsy", async () => {
    const wrapper = mountJobManager()
    await settle()

    const vm = wrapper.vm as any
    expect(vm.formatDate(undefined)).toBe("-")
    expect(vm.formatDate("")).toBe("-")
    expect(vm.formatDate("not-a-date")).toBe("not-a-date")
    expect(vm.formatDate("2026-01-15T10:30:00Z")).toMatch(/2026/)
  })

  it("looksLikeStackTrace identifies Java stack traces", async () => {
    const wrapper = mountJobManager()
    await settle()

    const vm = wrapper.vm as any
    expect(vm.looksLikeStackTrace("Error\n\tat com.example.Main.run(Main.java:42)")).toBe(true)
    expect(vm.looksLikeStackTrace("Error\n at something")).toBe(true)
    expect(vm.looksLikeStackTrace("Caused by: java.lang.NullPointerException")).toBe(true)
    expect(vm.looksLikeStackTrace("just a normal message")).toBe(false)
    expect(vm.looksLikeStackTrace(null)).toBe(false)
    expect(vm.looksLikeStackTrace(undefined)).toBe(false)
  })

  it("expanded row toggle shows and hides job details", async () => {
    const wrapper = mountJobManager()
    await settle()

    const vm = wrapper.vm as any
    const execution = {id: 1}

    expect(vm.isExpanded(execution)).toBe(false)

    vm.toggleExpanded(execution)
    expect(vm.isExpanded(execution)).toBe(true)

    vm.toggleExpanded(execution)
    expect(vm.isExpanded(execution)).toBe(false)
  })
})
