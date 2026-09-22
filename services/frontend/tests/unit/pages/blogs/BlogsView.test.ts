import {beforeEach, describe, expect, it, vi} from "vitest"
import BlogsView from "@/pages/blogs/BlogsView.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockRouterPush,
  mockListBlogs,
} = vi.hoisted(() => ({
  mockRouterPush: vi.fn(),
  mockListBlogs: vi.fn(),
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

vi.mock("@/domains/blogs", () => ({
  listBlogs: mockListBlogs,
}))

describe("BlogsView page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockListBlogs.mockResolvedValue([
      {id: "7", title: "January update", publishedAt: "2026-01-10T00:00:00.000Z"},
    ])
  })

  it("loads blogs and navigates to selected blog", async () => {
    const wrapper = mountInApp(BlogsView)
    await settle()

    expect(mockListBlogs).toHaveBeenCalledTimes(1)
    await wrapper.get(".v-list-item").trigger("click")
    expect(mockRouterPush).toHaveBeenCalledWith("/blogs/7")
  })

  it("says the listing could not be read rather than showing an association without newsletters", async () => {
    mockListBlogs.mockRejectedValue(new Error("refused"))

    const wrapper = mountInApp(BlogsView)
    await settle()

    expect(wrapper.text()).toContain("Failed to load newsletters")
  })
})
