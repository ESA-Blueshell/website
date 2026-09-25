import axios, {AxiosError, AxiosHeaders, type InternalAxiosRequestConfig} from "axios"
import {Buffer} from "./shims/buffer"
import {fixtures} from "./fixtures"
// The e2e suite's own api stand-in, driven here through a page that is only a list of routes.
import {installApiMocks, loginAsAdmin, loginAsBoard, loginAsMember, preferLightTheme} from "../../wt-games/services/frontend/tests/e2e/mocks"

type Fulfilment = {status?: number, contentType?: string, headers?: Record<string, string>, body?: string | Uint8Array, json?: unknown}
type Handler = (route: unknown) => unknown
const routes: {pattern: RegExp, handler: Handler}[] = []

const patternOf = (glob: string) => new RegExp(`^${glob.replace(/[.+^${}()|[\]\\?]/g, "\\$&").replace(/\*\*/g, "\u0000").replace(/\*/g, "[^/]*").replace(/\u0000/g, ".*")}$`)

const page = {
  route: async (glob: string, handler: Handler) => { routes.unshift({pattern: patternOf(glob), handler}) },
  addInitScript: async (script: (argument?: unknown) => void, argument?: unknown) => { script(argument) },
}
const context = {
  addCookies: async (cookies: {name: string, value: string}[]) => {
    for (const cookie of cookies) document.cookie = `${cookie.name}=${cookie.value}; path=/`
  },
}

const bodyText = (body: unknown): string | null => {
  if (body == null) return null
  if (typeof body === "string") return body
  if (body instanceof FormData) {
    return [...body.entries()].map(([key, value]) => value instanceof Blob ? `name="${key}"\r\nContent-Type: ${value.type}` : `name="${key}"\r\n\r\n${value}`).join("\r\n")
  }
  try { return JSON.stringify(body) } catch { return String(body) }
}

export function respond(url: string, method: string, body: unknown, headers: Record<string, string> = {}): Promise<Fulfilment> {
  const match = routes.find(one => one.pattern.test(url))
  if (!match) return Promise.resolve({status: 404, body: ""})
  const text = bodyText(body)
  const request = {
    url: () => url,
    method: () => method,
    // Who is asking travels in the cookie header, as it does from a browser.
    headers: () => ({cookie: document.cookie, ...headers}),
    postData: () => text,
    postDataJSON: () => { try { return text == null ? null : JSON.parse(text) } catch { return null } },
    postDataBuffer: () => (text == null ? null : Buffer.from(text)),
  }
  return new Promise(resolve => {
    const route = {
      request: () => request,
      fulfill: async (fulfilment: Fulfilment) => resolve(fulfilment),
      fallback: async () => resolve({status: 404, body: ""}),
      continue: async () => resolve({status: 404, body: ""}),
      abort: async () => resolve({status: 0, body: ""}),
    }
    Promise.resolve(match.handler(route)).catch(error => resolve({status: 500, body: String(error)}))
  }).then(fulfilment => new Promise(resolve => setTimeout(() => resolve(fulfilment as Fulfilment), 25)))
}

const API = "http://127.0.0.1:4173"
const toMockUrl = (uri: string) => {
  const at = uri.indexOf("/api/")
  return at >= 0 ? API + uri.slice(at) : uri
}
const decode = (body: Fulfilment["body"]) => (body instanceof Uint8Array ? new TextDecoder().decode(body) : body ?? "")

axios.defaults.adapter = async (config: InternalAxiosRequestConfig) => {
  const url = toMockUrl(axios.getUri(config))
  const headers = Object.fromEntries(Object.entries(AxiosHeaders.from(config.headers as never).toJSON()).map(([key, value]) => [key.toLowerCase(), String(value)]))
  const answer = await respond(url, (config.method ?? "get").toUpperCase(), config.data, headers)
  const status = answer.status ?? 200
  const contentType = answer.contentType ?? answer.headers?.["content-type"] ?? "application/json"
  let data: unknown = answer.json !== undefined ? JSON.stringify(answer.json) : answer.body ?? ""
  if (config.responseType === "blob") data = new Blob([data as BlobPart], {type: contentType})
  else if (config.responseType === "arraybuffer") data = new TextEncoder().encode(decode(data as string)).buffer
  else data = decode(data as string)
  const response = {data, status, statusText: String(status), headers: new AxiosHeaders({"content-type": contentType, ...(answer.headers ?? {})}), config, request: {}}
  if (!config.validateStatus || config.validateStatus(status)) return response
  throw new AxiosError(`Request failed with status code ${status}`, status >= 500 ? AxiosError.ERR_BAD_RESPONSE : AxiosError.ERR_BAD_REQUEST, config, {}, response as never)
}

const realFetch = window.fetch.bind(window)
window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
  const raw = typeof input === "string" ? input : input instanceof URL ? input.href : input.url
  const url = toMockUrl(new URL(raw, location.href === "about:srcdoc" ? API : location.href).href)
  if (!routes.some(one => one.pattern.test(url))) return realFetch(input, init)
  const answer = await respond(url, (init?.method ?? "GET").toUpperCase(), init?.body)
  const status = answer.status ?? 200
  const body = status === 204 || status === 304 ? null : answer.json !== undefined ? JSON.stringify(answer.json) : answer.body ?? ""
  return new Response(body as BodyInit | null, {status, headers: {"content-type": answer.contentType ?? "application/json", ...(answer.headers ?? {})}})
}

/** Installs the api stand-in for [viewer], in [theme]. */
export async function installMocks(viewer: string, theme: string) {
  await installApiMocks(page as never, fixtures as never)
  if (viewer === "board") await loginAsBoard(context as never)
  if (viewer === "member") await loginAsMember(context as never)
  if (viewer === "admin") await loginAsAdmin(context as never)
  if (theme === "light") await preferLightTheme(page as never)
}
