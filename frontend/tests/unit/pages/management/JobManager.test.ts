import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
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

vi.mock("@/services/api", () => ({
  list: mockList,
  retry: mockRetry,
}))

vi.mock("@/plugins/handleNetworkError", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("JobManager page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isAdmin = true
    mockList.mockResolvedValue({
      status: 200,
      data: [
        {id: 1, status: "FAILED", jobType: "SYNC", attempts: 1},
        {id: 2, status: "SUCCESS", jobType: "SYNC", attempts: 1},
      ],
    })
    mockRetry.mockResolvedValue({
      status: 200,
      data: {id: 1, status: "SUCCESS", jobType: "SYNC", attempts: 2},
    })
  })

  it("loads and retries jobs for admins", async () => {
    const wrapper = shallowMount(JobManager)
    await settle()

    expect(mockList).toHaveBeenCalledTimes(1)
    expect((wrapper.vm as any).executions).toHaveLength(2)

    await (wrapper.vm as any).retry({id: 1})
    expect(mockRetry).toHaveBeenCalledWith({path: {id: 1}})
    expect((wrapper.vm as any).executions.find((j: { id: number }) => j.id === 1)?.status).toBe("SUCCESS")
  })

  it("redirects non-admins", async () => {
    mockStore.getters.isAdmin = false

    shallowMount(JobManager)
    await settle()

    expect(mockRouterReplace).toHaveBeenCalledWith("/")
    expect(mockList).not.toHaveBeenCalled()
  })
})
