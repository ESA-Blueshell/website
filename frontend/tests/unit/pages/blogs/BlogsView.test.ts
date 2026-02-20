import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import BlogsView from "@/pages/blogs/BlogsView.vue"
import {settle} from "../helpers"

const {
  mockRouterPush,
  mockFindBlogs,
} = vi.hoisted(() => ({
  mockRouterPush: vi.fn(),
  mockFindBlogs: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRouter: () => ({
      push: mockRouterPush,
    }),
  }
})

vi.mock("@/services/api", () => ({
  findBlogs: mockFindBlogs,
}))

describe("BlogsView page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindBlogs.mockResolvedValue({
      data: [{id: "7", title: "January update", publishedAt: "2026-01-10T00:00:00.000Z"}],
    })
  })

  it("loads blogs and navigates to selected blog", async () => {
    const wrapper = shallowMount(BlogsView)
    await settle()

    expect(mockFindBlogs).toHaveBeenCalledTimes(1)
    await (wrapper.vm as any).navigateToBlog("7")
    expect(mockRouterPush).toHaveBeenCalledWith("/blogs/7")
  })
})
