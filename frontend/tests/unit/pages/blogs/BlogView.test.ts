import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import BlogView from "@/pages/blogs/BlogView.vue"
import {settle} from "../helpers"

const {
  mockRoute,
  mockFindBlogById,
} = vi.hoisted(() => ({
  mockRoute: {
    params: {id: "9"},
  },
  mockFindBlogById: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
  }
})

vi.mock("@/services/api", () => ({
  findBlogById: mockFindBlogById,
}))

vi.mock("axios", () => ({
  default: {
    isAxiosError: (err: unknown) => Boolean((err as {response?: unknown})?.response),
  },
}))

describe("BlogView page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.params = {id: "9"}
  })

  it("renders iframe when blog is returned", async () => {
    mockFindBlogById.mockResolvedValue({
      data: {
        id: 9,
        html: "<h1>Blog</h1>",
      },
    })

    const wrapper = shallowMount(BlogView)
    await settle()

    expect(mockFindBlogById).toHaveBeenCalledWith({
      path: {id: 9},
      throwOnError: true,
    })
    expect(wrapper.get("iframe").attributes("srcdoc")).toContain("<h1>Blog</h1>")
  })

  it("shows not-found state on 404", async () => {
    mockFindBlogById.mockRejectedValue({
      response: {status: 404},
    })

    const wrapper = shallowMount(BlogView)
    await settle()

    expect(wrapper.text()).toContain("Blog not found")
  })
})
