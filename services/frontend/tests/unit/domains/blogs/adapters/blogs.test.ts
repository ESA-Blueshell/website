import {describe, expect, it, vi} from "vitest"
import {listBlogs, readBlog} from "@/domains/blogs/adapters/blogs"
import {findBlogById, findBlogs} from "@/services/api"
import {aBlog} from "../../../helpers/apiFixtures"
import {answer, emptyAnswer} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findBlogs: vi.fn(),
  findBlogById: vi.fn(),
}))

const refusal = (status: number) => ({isAxiosError: true, response: {status}})

describe("listBlogs", () => {
  it("answers with the newsletters it read", async () => {
    vi.mocked(findBlogs).mockResolvedValue(answer(findBlogs, [aBlog()]))

    await expect(listBlogs()).resolves.toEqual([aBlog()])
  })

  it("reads an answer without a body as no newsletters", async () => {
    vi.mocked(findBlogs).mockResolvedValue(emptyAnswer(findBlogs))

    await expect(listBlogs()).resolves.toEqual([])
  })

  it("throws on a refusal rather than answering with an empty listing", async () => {
    vi.mocked(findBlogs).mockRejectedValue(refusal(500))

    await expect(listBlogs()).rejects.toBeDefined()
  })
})

describe("readBlog", () => {
  it("answers with the newsletter behind the number", async () => {
    vi.mocked(findBlogById).mockResolvedValue(answer(findBlogById, aBlog({id: 9})))

    await expect(readBlog(9)).resolves.toEqual(aBlog({id: 9}))
  })

  it("reads an answer without a body as no newsletter", async () => {
    vi.mocked(findBlogById).mockResolvedValue(emptyAnswer(findBlogById))

    await expect(readBlog(9)).resolves.toBeNull()
  })

  it("answers with nothing when there is no such newsletter", async () => {
    vi.mocked(findBlogById).mockRejectedValue(refusal(404))

    await expect(readBlog(9)).resolves.toBeNull()
  })

  // A newsletter that could not be read is not a newsletter that is not there.
  it("throws when the read was refused for any other reason", async () => {
    vi.mocked(findBlogById).mockRejectedValue(refusal(503))

    await expect(readBlog(9)).rejects.toBeDefined()
  })

  it("throws when the read failed without an answer at all", async () => {
    vi.mocked(findBlogById).mockRejectedValue(new Error("offline"))

    await expect(readBlog(9)).rejects.toBeDefined()
  })
})
