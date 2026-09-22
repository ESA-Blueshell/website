/**
 * The blog domain's public API: what a page may reach for, and nothing else (frontend ADR-001).
 * Re-exported by name rather than with `export *`, because the list of names is the promise.
 */
export {listBlogs, readBlog} from "./adapters/blogs"
export type {BlogResponse} from "@/services/api"
