import {beforeEach, describe, expect, it, vi} from "vitest"
import BlogView from "@/pages/blogs/BlogView.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockRoute,
  mockReadBlog,
} = vi.hoisted(() => ({
  mockRoute: {
    params: {id: "9"},
  },
  mockReadBlog: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
  }
})

vi.mock("@/domains/blogs", () => ({
  readBlog: mockReadBlog,
}))

describe("BlogView page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.params = {id: "9"}
  })

  it("renders hardened iframe when blog is returned", async () => {
    mockReadBlog.mockResolvedValue({id: 9, html: "<h1>Blog</h1>"})

    const wrapper = mountInApp(BlogView)
    await settle()

    expect(mockReadBlog).toHaveBeenCalledWith(9)
    const iframe = wrapper.get("iframe")
    expect(iframe.attributes("srcdoc")).toContain("<h1>Blog</h1>")
    expect(iframe.attributes("sandbox")).toBe("")
    expect(iframe.attributes("referrerpolicy")).toBe("no-referrer")
  })

  it("names the tab after the newsletter it shows", async () => {
    mockReadBlog.mockResolvedValue({id: 9, title: "September newsletter", html: "<h1>Blog</h1>"})

    mountInApp(BlogView)
    await settle()

    expect(document.title).toBe("September newsletter — Blueshell Esports")
  })

  it("shows not-found state when there is no such blog", async () => {
    mockReadBlog.mockResolvedValue(null)

    const wrapper = mountInApp(BlogView)
    await settle()

    expect(wrapper.text()).toContain("Blog not found")
  })

  it("says the blog could not be read rather than that it is not there", async () => {
    mockReadBlog.mockRejectedValue(new Error("refused"))

    const wrapper = mountInApp(BlogView)
    await settle()

    expect(wrapper.text()).toContain("Failed to load blog")
  })
})
