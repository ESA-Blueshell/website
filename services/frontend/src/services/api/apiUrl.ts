/*
 * Where the api answers, in a module that imports nothing: a page can build a link to the api
 * without loading the SDK, and a test that mocks the SDK leaves these alone.
 */

/**
 * The api is reached under `/api` at the page's own origin, in every environment.
 *
 * Nothing configures the host: Traefik, the nginx image and the vite dev server each
 * strip the prefix in front of the api, so the bundle is identical everywhere.
 */
export function resolveBaseURL(): string {
  if (typeof window !== "undefined") return `${window.location.origin}/api`
  // No window (SSR, unit runner). Nothing calls the api from here.
  return "https://localhost/api"
}

/**
 * Where a path the api handed back is actually served.
 *
 * The api answers with paths of its own rather than absolute urls, since it has no way of
 * knowing what sits in front of it. A bare path would resolve against the page's origin,
 * which is the frontend and not the api: the api answers under `/api` on the same host.
 */
export function apiUrl(path: string): string {
  if (/^https?:\/\//.test(path)) return path
  return `${resolveBaseURL().replace(/\/$/, "")}${path.startsWith("/") ? path : `/${path}`}`
}
