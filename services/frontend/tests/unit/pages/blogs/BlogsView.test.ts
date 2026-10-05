import {beforeEach, describe, expect, it, vi} from "vitest"
import {RouterLinkStub} from "@vue/test-utils"
import BlogsView from "@/pages/blogs/BlogsView.vue"
import {mountInApp, settle} from "../helpers"

const {mockListBlogs} = vi.hoisted(() => ({mockListBlogs: vi.fn()}))

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

  it("loads blogs and links each to its own page", async () => {
    const wrapper = mountInApp(BlogsView, {global: {stubs: {RouterLink: RouterLinkStub}}})
    await settle()

    expect(mockListBlogs).toHaveBeenCalledTimes(1)
    const row = wrapper.getComponent(RouterLinkStub)
    expect(row.text()).toContain("January update")
    expect(row.props("to")).toBe("/blogs/7")
  })

  it("says the listing could not be read rather than showing an association without newsletters", async () => {
    mockListBlogs.mockRejectedValue(new Error("refused"))

    const wrapper = mountInApp(BlogsView)
    await settle()

    expect(wrapper.text()).toContain("Failed to load newsletters")
  })
})
