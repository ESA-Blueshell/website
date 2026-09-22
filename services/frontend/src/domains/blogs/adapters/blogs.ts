/**
 * Blog domain adapter — the only file in this domain that imports from `@/services/api`
 * (frontend ADR-002). Everything else comes through the door beside it.
 */
import axios from "axios"
import {type BlogResponse, findBlogById, findBlogs} from "@/services/api"

/**
 * Every newsletter. Throws on a refusal rather than answering with an empty listing: a listing
 * that could not be read is not an association without newsletters, and the page says so.
 */
export async function listBlogs(): Promise<BlogResponse[]> {
  const res = await findBlogs({throwOnError: true})
  return (res.data ?? []) as BlogResponse[]
}

/**
 * The newsletter behind the number, or nothing when there is no such newsletter. Any other
 * refusal throws, because a newsletter that could not be read is not one that is not there.
 */
export async function readBlog(id: number): Promise<BlogResponse | null> {
  try {
    const res = await findBlogById({path: {id}, throwOnError: true})
    return (res.data ?? null) as BlogResponse | null
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      return null
    }
    throw error
  }
}
